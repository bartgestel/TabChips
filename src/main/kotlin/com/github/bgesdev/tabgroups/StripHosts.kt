package com.github.bgesdev.tabgroups

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.ui.ThreeComponentsSplitter
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationProvider
import com.intellij.openapi.wm.impl.IdeBackgroundUtil
import com.intellij.ui.EditorNotifications
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import java.util.function.Function
import javax.swing.JComponent
import javax.swing.JPanel

private val LOG = logger<StripPlacement>()

/** Decides where the strip lives. Every host shows the same [TabGroupStrip]; only the attach point differs. */
interface StripHost {
    /** Returns false if this host cannot attach in the current IDE layout. */
    fun attach(): Boolean
    fun detach()
}

/** Host A (default, public API): one strip per editor below its tab row, via [NotificationStripProvider]. */
class NotificationStripHost(private val project: Project) : StripHost {
    override fun attach(): Boolean = true.also { refresh() }
    override fun detach() = refresh()
    // The provider asks StripPlacement whether Host A is active, so a refresh is all attach/detach needs.
    private fun refresh() = EditorNotifications.getInstance(project).updateAllNotifications()
}

class NotificationStripProvider : EditorNotificationProvider, DumbAware {
    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? =
        if (StripPlacement.getInstance(project).host is NotificationStripHost) Function { TabGroupStrip(project) } else null
}

/**
 * Host B (experimental): one shared strip above all tab rows.
 * Internal API used here: [FileEditorManagerEx.getSplitters]. The editor area sits as the inner component of the frame's
 * [ThreeComponentsSplitter]; we swap in a wrapper panel with the strip on top and the splitters below.
 * Any other layout, any exception, or the IDE later replacing our wrapper → [attach] returns false / [onBroken].
 */
class SplittersStripHost(private val project: Project, private val onBroken: () -> Unit) : StripHost {
    private var frameSplitter: ThreeComponentsSplitter? = null
    private var wrapper: JPanel? = null
    private var splitters: JComponent? = null
    private val watcher = object : ContainerAdapter() {
        override fun componentRemoved(e: ContainerEvent) {
            // Deferred: the IDE is mid-way through swapping components; don't fight it, fall back afterwards.
            if (e.child === wrapper || e.child === splitters) {
                ApplicationManager.getApplication().invokeLater(onBroken, project.disposed)
            }
        }
    }

    override fun attach(): Boolean = try {
        val splitters = FileEditorManagerEx.getInstanceEx(project).splitters
        val frameSplitter = splitters.parent as? ThreeComponentsSplitter
        if (frameSplitter == null || frameSplitter.innerComponent !== splitters) {
            LOG.warn("Shared strip: unexpected editor layout (parent=${splitters.parent?.javaClass?.name})")
            false
        } else {
            val wrapper = JPanel(BorderLayout()).apply {
                isOpaque = false // let the window background show in the gap around the island
                add(IslandFrame(TabGroupStrip(project), splitters), BorderLayout.NORTH)
                add(splitters, BorderLayout.CENTER) // reparents the splitters
            }
            frameSplitter.innerComponent = wrapper
            frameSplitter.addContainerListener(watcher)
            wrapper.addContainerListener(watcher)
            this.frameSplitter = frameSplitter; this.wrapper = wrapper; this.splitters = splitters
            LOG.info("Shared strip attached above the editor tabs")
            true
        }
    } catch (e: Throwable) { // also LinkageError if the internal API changed shape
        if (e is ControlFlowException) throw e
        LOG.warn("Shared strip: could not attach, falling back to per-editor strips", e)
        false
    }

    override fun detach() {
        val frameSplitter = frameSplitter ?: return
        val wrapper = wrapper; val splitters = splitters
        this.frameSplitter = null; this.wrapper = null; this.splitters = null // also stops the watcher re-firing
        frameSplitter.removeContainerListener(watcher)
        wrapper?.removeContainerListener(watcher)
        // Put the editor area back only if our wrapper is still in place (the IDE may have replaced it already).
        if (wrapper != null && splitters != null && frameSplitter.innerComponent === wrapper) {
            wrapper.remove(splitters)
            frameSplitter.innerComponent = splitters
        }
        frameSplitter.revalidate()
        frameSplitter.repaint()
    }
}

/**
 * Host B chrome: in Islands themes, draws the strip as a rounded island (theme's `Island.arc`) lined up with the
 * editor island below it; in classic themes it is the plain bar. Re-evaluated on theme change via [updateUI].
 */
private class IslandFrame(private val strip: TabGroupStrip, private val editorArea: JComponent) : JPanel(BorderLayout()) {
    init {
        add(strip)
        updateUI()
    }

    private val arc get() = JBUI.scale(JBUI.getInt("Island.arc", 0))

    override fun updateUI() {
        super.updateUI()
        @Suppress("SENSELESS_COMPARISON") // updateUI runs from the JPanel constructor, before `strip` is set
        if (strip == null) return
        val island = arc > 0
        isOpaque = !island
        strip.isOpaque = !island
        strip.border = if (island) JBUI.Borders.empty(3, 8) else TabGroupStrip.defaultBorder()
    }

    // Same left/right inset as the editor island, so both islands share their edges.
    override fun getInsets(): Insets =
        if (arc > 0) editorArea.insets.let { JBInsets(0, it.left, 0, it.right) } else super.getInsets()

    override fun paintComponent(g: Graphics) {
        val arc = arc
        if (arc == 0) return super.paintComponent(g)
        val i = insets
        // Fill our whole area with the window color through the frame-background graphics: the IDE swaps in its
        // window gradient for that color, so the area around the rounded corners matches the gaps between islands.
        IdeBackgroundUtil.withFrameBackground(g, this).apply {
            color = JBColor.namedColor("MainWindow.background", parent?.background ?: background)
            fillRect(0, 0, width, height)
        }
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = JBUI.CurrentTheme.EditorTabs.background()
            g2.fill(RoundRectangle2D.Float(
                i.left.toFloat(), 0f, (width - i.left - i.right).toFloat(), height.toFloat(), arc.toFloat(), arc.toFloat(),
            ))
        } finally {
            g2.dispose()
        }
    }
}

/** Per project: holds the active host and switches between them. All calls on the EDT. */
@Service(Service.Level.PROJECT)
class StripPlacement(private val project: Project) : Disposable {
    @Volatile // read by NotificationStripProvider on a background thread
    var host: StripHost = NotificationStripHost(project)
        private set

    /** Matches the host to the current setting; Host B failing to attach leaves Host A in place. */
    fun apply() {
        val wantShared = TabGroupsSettings.getInstance().state.sharedStrip
        if (wantShared == (host is SplittersStripHost)) return
        switchTo(if (wantShared) SplittersStripHost(project, ::fallBack) else NotificationStripHost(project))
    }

    private fun switchTo(next: StripHost) {
        host.detach()
        host = next
        if (!next.attach()) fallBack()
    }

    private fun fallBack() {
        if (host is NotificationStripHost) return
        LOG.warn("Shared strip unavailable; using the per-editor strip below the tabs")
        switchTo(NotificationStripHost(project))
    }

    override fun dispose() = host.detach()

    companion object {
        fun getInstance(project: Project): StripPlacement = project.service()
    }
}

class StripPlacementStartup : ProjectActivity {
    override suspend fun execute(project: Project) = withContext(Dispatchers.EDT) {
        StripPlacement.getInstance(project).apply()
    }
}
