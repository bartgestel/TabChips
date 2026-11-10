package com.github.bgesdev.tabgroups

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AutoGroupTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        TabGroupsService.getInstance(project).loadState(TabGroupsState())
        TabGroupsSettings.getInstance().state.autoRules = mutableListOf(AutoGroupRule("Kotlin", "kt"))
    }

    override fun tearDown() {
        try {
            TabGroupsSettings.getInstance().state.autoRules = mutableListOf()
        } finally {
            super.tearDown()
        }
    }

    fun testOpeningAMatchingFileAddsItToTheRuleGroup() {
        val editors = FileEditorManager.getInstance(project)
        val kt = myFixture.addFileToProject("Main.kt", "").virtualFile
        val txt = myFixture.addFileToProject("notes.txt", "").virtualFile
        val manual = myFixture.addFileToProject("Manual.kt", "").virtualFile
        val service = TabGroupsService.getInstance(project)
        service.update { create("mine").also { add(it, manual.url) } }

        listOf(kt, txt, manual).forEach { editors.openFile(it, false) }

        assertEquals(listOf(kt.url), service.model.groups.single { it.name == "Kotlin" }.files) // created and filled
        assertNull(service.model.groupOf(txt.url)) // no rule matches
        assertEquals("mine", service.model.groupOf(manual.url)?.name) // already grouped: left alone
    }
}
