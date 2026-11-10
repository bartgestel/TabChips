package com.github.bgesdev.tabgroups

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.messages.Topic
import org.jetbrains.concurrency.CancellablePromise

fun interface TabGroupsListener {
    fun changed()
}

/** Single source of truth for a project's groups. Stored in the workspace file (not shared via VCS). */
@Service(Service.Level.PROJECT)
@State(name = "TabGroups", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class TabGroupsService(private val project: Project) : PersistentStateComponent<TabGroupsState>, Disposable {

    var model = TabGroupsState()
        private set

    override fun getState() = model

    override fun loadState(state: TabGroupsState) {
        model = state
    }

    /** All mutations go through here, on the EDT, and notify every strip afterwards. */
    fun update(block: TabGroupsState.() -> Unit) {
        ThreadingAssertions.assertEventDispatchThread()
        model.block()
        changed()
    }

    /**
     * Collapse closes the group's open tabs; expand reopens all of its files that still exist.
     * Missing files are skipped, so a deleted file never breaks collapse/expand.
     * With the "one group open at a time" setting, expanding first collapses every other expanded group.
     * Returns the pending lookup when expanding (tests wait on it), null when collapsing.
     */
    fun toggle(group: TabGroup): CancellablePromise<*>? {
        val editors = FileEditorManager.getInstance(project)
        val closing = if (!group.collapsed) listOf(group)
        else if (TabGroupsSettings.getInstance().state.exclusiveGroups) model.groups.filter { it !== group && !it.collapsed }
        else emptyList()
        update {
            group.collapsed = !group.collapsed
            closing.forEach { it.collapsed = true }
        }
        val closeUrls = closing.flatMapTo(HashSet()) { it.files }
        editors.openFiles.filter { it.url in closeUrls }.forEach(editors::closeFile) // no VFS lookup needed
        if (group.collapsed) return null
        // ponytail: openFile is deprecated in 263 for requestOpenFile(file, FileEditorOpenRequest), which 262 lacks.
        // Switch both call sites when since-build moves to 263.
        return resolve(group.files.toList()) { files -> files.forEach { editors.openFile(it, false) } }
    }

    /** Looks files up off the EDT (VFS lookups may hit disk), then hands the existing ones to [then] on the EDT. */
    fun resolve(urls: List<String>, then: (List<VirtualFile>) -> Unit): CancellablePromise<List<VirtualFile>> =
        ReadAction.nonBlocking<List<VirtualFile>> {
            urls.mapNotNull { VirtualFileManager.getInstance().findFileByUrl(it)?.takeIf(VirtualFile::isValid) }
        }
            .expireWith(this)
            .finishOnUiThread(ModalityState.defaultModalityState(), then)
            .submit(AppExecutorUtil.getAppExecutorService())

    override fun dispose() = Unit

    fun changed() = project.messageBus.syncPublisher(TOPIC).changed()

    companion object {
        @Topic.ProjectLevel
        val TOPIC = Topic.create("TabGroups changed", TabGroupsListener::class.java)

        fun getInstance(project: Project): TabGroupsService = project.service()
    }
}

/** Registered in plugin.xml: redraw strips when tabs open, close or the selection moves (active highlight). */
class EditorEventsListener(private val project: Project) : FileEditorManagerListener {
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) = refresh()
    override fun fileClosed(source: FileEditorManager, file: VirtualFile) = refresh()
    override fun selectionChanged(event: FileEditorManagerEvent) = refresh()
    private fun refresh() = TabGroupsService.getInstance(project).changed()
}
