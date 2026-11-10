package com.github.bgesdev.tabgroups

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem
import com.intellij.ui.FileColorManager
import com.intellij.ui.JBColor
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.WrapLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * The chip strip. Knows nothing about where it is placed: hosts just `add(TabGroupStrip(project))`.
 * It holds no state of its own; it redraws from [TabGroupsService] whenever the service changes.
 */
class TabGroupStrip(private val project: Project) : JPanel(WrapLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(2))) {
    private var connection: MessageBusConnection? = null

    init {
        background = JBUI.CurrentTheme.EditorTabs.background()
        border = defaultBorder()
    }

    companion object {
        fun defaultBorder() = JBUI.Borders.compound(JBUI.Borders.customLineBottom(JBColor.border()), JBUI.Borders.empty(1, 4))
    }

    override fun addNotify() {
        super.addNotify()
        if (project.isDisposed) return
        connection = project.messageBus.connect().also {
            it.subscribe(TabGroupsService.TOPIC, TabGroupsListener { rebuild() })
        }
        rebuild()
    }

    override fun removeNotify() {
        connection?.disconnect()
        connection = null
        super.removeNotify()
    }

    private fun rebuild() {
        removeAll()
        val service = TabGroupsService.getInstance(project)
        val activeUrl = FileEditorManager.getInstance(project).selectedEditor?.file?.url
        for (group in service.model.groups) {
            val color = { FileColorManager.getInstance(project).getColor(group.color) }
            val name = if (group.collapsed) "${group.name} (${group.files.size})" else group.name
            add(Chip(name, color, bold = true, active = activeUrl != null && activeUrl in group.files).apply {
                toolTipText = "Click to ${if (group.collapsed) "expand and reopen" else "collapse and close"} its tabs"
                onClick = { service.toggle(group) }
                onMenu = { showGroupMenu(project, group, it) }
            })
            if (!group.collapsed) group.files.forEach { add(fileChip(it, color, active = it == activeUrl)) }
        }
        // Files not in the VFS cache yet (e.g. right after a restart) show as missing; look them up in the
        // background and redraw once if any turn out to exist. Truly missing files are never found → no loop.
        val uncached = service.model.groups.filter { !it.collapsed }.flatMap { it.files }.filter { cachedFile(it) == null }
        if (uncached.isNotEmpty()) service.resolve(uncached) { found -> if (found.isNotEmpty()) service.changed() }
        add(Chip("+", { null }, bold = true).apply {
            toolTipText = "New tab group (adds the current file)"
            onClick = { newGroupDialog(project) }
        })
        revalidate()
        repaint()
    }

    private fun fileChip(url: String, color: () -> Color?, active: Boolean): Chip {
        val file = cachedFile(url)
        return Chip(file?.name ?: url.substringAfterLast('/'), color, bold = false, active).apply {
            font = JBUI.Fonts.smallFont()
            onMenu = { showFileMenu(project, url, it) }
            if (file == null) {
                foreground = UIUtil.getInactiveTextColor()
                toolTipText = "Missing: $url"
            } else {
                toolTipText = file.presentableUrl
                // Deprecated in 263; see TabGroupsService.toggle for why it stays for now.
                onClick = { FileEditorManager.getInstance(project).openFile(file, true) }
            }
        }
    }
}

/** Cache-only VFS lookup: never touches disk, so it is safe on the EDT. Null = not cached or deleted. */
private fun cachedFile(url: String): VirtualFile? {
    val fs = VirtualFileManager.getInstance().getFileSystem(VirtualFileManager.extractProtocol(url)) as? NewVirtualFileSystem
    return fs?.findFileByPathIfCached(VirtualFileManager.extractPath(url))?.takeIf { it.isValid }
}

/**
 * Rounded label. [bg] is resolved at paint time so a theme switch repaints in the new theme's colors;
 * null means no fill. [active] draws a focus-colored outline (the selected file or its group).
 */
private class Chip(text: String, private val bg: () -> Color?, bold: Boolean, private val active: Boolean = false) :
    JLabel(text) {
    var onClick: (() -> Unit)? = null
    var onMenu: ((MouseEvent) -> Unit)? = null

    init {
        border = JBUI.Borders.empty(1, 8)
        if (bold) font = JBUI.Fonts.label().asBold()
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e) && !e.isPopupTrigger) onClick?.invoke()
            }

            // Popup trigger is on press (Linux/macOS) or release (Windows).
            override fun mousePressed(e: MouseEvent) = popup(e)
            override fun mouseReleased(e: MouseEvent) = popup(e)
            private fun popup(e: MouseEvent) {
                if (e.isPopupTrigger) onMenu?.invoke(e)
            }
        })
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val arc = JBUI.scale(10).toFloat()
        bg()?.let {
            g2.color = it
            g2.fill(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), arc, arc))
        }
        if (active) {
            val w = JBUI.scale(1).toFloat()
            g2.color = JBUI.CurrentTheme.Focus.focusColor()
            g2.stroke = java.awt.BasicStroke(w)
            g2.draw(RoundRectangle2D.Float(w / 2, w / 2, width - w, height - w, arc, arc))
        }
        g2.dispose()
        super.paintComponent(g)
    }
}

/** Asks for a name and creates a group containing the currently selected file, if any. */
fun newGroupDialog(project: Project, url: String? = FileEditorManager.getInstance(project).selectedEditor?.file?.url) {
    val name = Messages.showInputDialog(project, "Group name:", "New Tab Group", null)?.trim()
    if (name.isNullOrEmpty()) return
    TabGroupsService.getInstance(project).update {
        val group = create(name)
        if (url != null) add(group, url)
    }
}
