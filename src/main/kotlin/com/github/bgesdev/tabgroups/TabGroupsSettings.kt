package com.github.bgesdev.tabgroups

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import kotlin.reflect.KMutableProperty0

@Service(Service.Level.APP)
@State(name = "TabGroupsSettings", storages = [Storage("tabGroups.xml")])
class TabGroupsSettings : PersistentStateComponent<TabGroupsSettings.Data> {
    class Data(
        var sharedStrip: Boolean = false,
        var exclusiveGroups: Boolean = false,
        var autoRules: MutableList<AutoGroupRule> = mutableListOf(),
    )

    private var data = Data()
    override fun getState() = data
    override fun loadState(state: Data) { data = state }

    companion object {
        fun getInstance(): TabGroupsSettings = service()
    }
}

/** Settings | Tools | Tab Groups */
class TabGroupsConfigurable : BoundConfigurable("Tab Groups") {
    private val settings get() = TabGroupsSettings.getInstance().state

    private val rulesModel = ListTableModel<AutoGroupRule>(
        column("Group", "e.g. Kotlin") { it::group },
        column("Extensions", "e.g. kt, kts or $TABLES_KEYWORD") { it::extensions },
    )
    private val rulesTable = TableView(rulesModel).apply {
        setShowGrid(false)
        visibleRowCount = 5
    }

    override fun createPanel() = panel {
        group("Auto-Grouping") {
            row {
                comment("When you open a file that isn't in a group yet, it joins the group whose extensions match. " +
                    "The group is created if it doesn't exist. Use <code>$TABLES_KEYWORD</code> for database tables and views.")
            }
            row {
                val decorated = ToolbarDecorator.createDecorator(rulesTable)
                    .setAddAction {
                        rulesTable.stopEditing()
                        rulesModel.addRow(AutoGroupRule())
                        val row = rulesModel.rowCount - 1
                        rulesTable.selection = listOf(rulesModel.getItem(row))
                        rulesTable.editCellAt(row, 0)
                        rulesTable.editorComponent?.requestFocusInWindow()
                    }
                    .disableUpDownActions()
                    .createPanel()
                cell(decorated).align(Align.FILL)
            }.resizableRow()
        }
        row {
            checkBox("Only one group open at a time")
                .bindSelected(settings::exclusiveGroups)
                .comment("Expanding a group collapses the open one and closes its tabs. Ungrouped tabs stay open.")
        }
        row {
            checkBox("Show one shared strip above the editor tabs (experimental)")
                .bindSelected(settings::sharedStrip)
                .comment("Relies on IDE internals. If it cannot attach, the strip below each editor's tabs is used instead.")
        }

        // The table edits copies; settings only change on Apply.
        onReset { rulesModel.items = settings.autoRules.map { it.copy() } }
        // Polled by the dialog several times a second, so it must not touch the cell editor:
        // stopping it here would cut off typing. An open editor simply counts as a change.
        onIsModified { rulesTable.isEditing || editedRules() != settings.autoRules }
        onApply {
            rulesTable.stopEditing() // commit the cell still being typed in
            settings.autoRules = editedRules().toMutableList()
        }
    }

    /** Rows as edited, without completely empty ones. */
    private fun editedRules(): List<AutoGroupRule> =
        rulesModel.items.filter { it.group.isNotBlank() || it.extensions.isNotBlank() }.map { it.copy() }

    override fun apply() {
        super.apply()
        ProjectManager.getInstance().openProjects.forEach { StripPlacement.getInstance(it).apply() }
    }
}

/** An editable text column bound to one property of the row. */
private fun column(name: String, hint: String, property: (AutoGroupRule) -> KMutableProperty0<String>) =
    object : ColumnInfo<AutoGroupRule, String>(name) {
        override fun valueOf(item: AutoGroupRule) = property(item).get()
        override fun setValue(item: AutoGroupRule, value: String) = property(item).set(value.trim())
        override fun isCellEditable(item: AutoGroupRule) = true
        override fun getTooltipText() = hint
    }
