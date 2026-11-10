package com.github.bgesdev.tabgroups

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.FileColorManager
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.JBUI
import java.awt.event.MouseEvent

// ---- Editor tab context menu (EditorTabPopupMenu) ----

private val AnActionEvent.tabUrl get() = getData(CommonDataKeys.VIRTUAL_FILE)?.url

private val AnActionEvent.service get() = project?.let { TabGroupsService.getInstance(it) }

/** "Add to Group ▸ <groups>". Hidden when there are no groups yet. */
class AddToGroupActionGroup : ActionGroup(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.tabUrl != null && e.service?.model?.groups?.isNotEmpty() == true
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val service = e?.service ?: return EMPTY_ARRAY
        val url = e.tabUrl ?: return EMPTY_ARRAY
        val current = service.model.groupOf(url)
        return service.model.groups.toList().map { group ->
            object : DumbAwareAction(group.name) {
                override fun getActionUpdateThread() = ActionUpdateThread.BGT
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = group !== current // already in this group
                }
                override fun actionPerformed(e: AnActionEvent) = service.update { add(group, url) }
            }
        }.toTypedArray()
    }
}

class NewGroupFromTabAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && e.tabUrl != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        newGroupDialog(e.project ?: return, e.tabUrl ?: return)
    }
}

class RemoveFromGroupAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val url = e.tabUrl
        e.presentation.isEnabledAndVisible = url != null && e.service?.model?.groupOf(url) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val url = e.tabUrl ?: return
        e.service?.update { remove(url) }
    }
}

/**
 * Window ▸ Tab Chips ▸ <group>: collapse/expand without the strip. Needed with Host A, where collapsing
 * the last open tabs leaves no editor (and so no strip) to click.
 */
class ToggleGroupsActionGroup : ActionGroup(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.service?.model?.groups?.isNotEmpty() == true
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val service = e?.service ?: return EMPTY_ARRAY
        return service.model.groups.toList().map { group ->
            val verb = if (group.collapsed) "Expand" else "Collapse"
            DumbAwareAction.create("$verb \"${group.name}\"") { service.toggle(group) }
        }.toTypedArray()
    }
}

// ---- Chip right-click menus (built on the fly; they act on one specific group or file) ----

fun showGroupMenu(project: Project, group: TabGroup, e: MouseEvent) {
    val service = TabGroupsService.getInstance(project)
    val colors = FileColorManager.getInstance(project)
    val menu = DefaultActionGroup(
        DumbAwareAction.create("Rename…") {
            val name = Messages.showInputDialog(project, "Group name:", "Rename Tab Group", null, group.name, null)?.trim()
            if (!name.isNullOrEmpty()) service.update { rename(group, name) }
        },
        DefaultActionGroup.createPopupGroup { "Color" }.apply {
            GROUP_COLORS.forEach { id ->
                val icon = colors.getColor(id)?.let { ColorIcon(JBUI.scale(12), it) }
                add(object : DumbAwareAction(colors.getColorName(id), null, icon) {
                    override fun actionPerformed(e: AnActionEvent) = service.update { group.color = id }
                })
            }
        },
        DumbAwareAction.create("Delete Group") {
            val ok = Messages.showOkCancelDialog(
                project, "Delete group \"${group.name}\"? Its files stay untouched.", "Delete Tab Group",
                "Delete", Messages.getCancelButton(), null,
            )
            if (ok == Messages.OK) service.update { delete(group) }
        },
    )
    show(menu, e)
}

fun showFileMenu(project: Project, url: String, e: MouseEvent) =
    show(DefaultActionGroup(DumbAwareAction.create("Remove from Group") {
        TabGroupsService.getInstance(project).update { remove(url) }
    }), e)

private fun show(group: ActionGroup, e: MouseEvent) =
    ActionManager.getInstance().createActionPopupMenu("TabGroupsChip", group).component.show(e.component, e.x, e.y)
