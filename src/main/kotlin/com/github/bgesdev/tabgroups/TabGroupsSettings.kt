package com.github.bgesdev.tabgroups

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

@Service(Service.Level.APP)
@State(name = "TabGroupsSettings", storages = [Storage("tabGroups.xml")])
class TabGroupsSettings : PersistentStateComponent<TabGroupsSettings.Data> {
    class Data(var sharedStrip: Boolean = false, var exclusiveGroups: Boolean = false)

    private var data = Data()
    override fun getState() = data
    override fun loadState(state: Data) { data = state }

    companion object {
        fun getInstance(): TabGroupsSettings = service()
    }
}

/** Settings | Tools | Tab Groups */
class TabGroupsConfigurable : BoundConfigurable("Tab Groups") {
    override fun createPanel() = panel {
        val settings = TabGroupsSettings.getInstance().state
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
    }

    override fun apply() {
        super.apply()
        ProjectManager.getInstance().openProjects.forEach { StripPlacement.getInstance(it).apply() }
    }
}
