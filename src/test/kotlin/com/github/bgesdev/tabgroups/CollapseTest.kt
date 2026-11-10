package com.github.bgesdev.tabgroups

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CollapseTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        TabGroupsService.getInstance(project).loadState(TabGroupsState()) // light project is shared between tests
    }

    fun testCollapseClosesTabsAndExpandReopensThem() {
        val editors = FileEditorManager.getInstance(project)
        val files = listOf("A.txt", "B.txt", "C.txt").map { myFixture.addFileToProject(it, "x").virtualFile }
        val service = TabGroupsService.getInstance(project)
        lateinit var group: TabGroup
        service.update {
            group = create("three")
            files.forEach { add(group, it.url) }
            add(group, "file:///gone/D.txt") // a deleted file must not break anything
        }
        files.forEach { editors.openFile(it, false) }

        assertNull(service.toggle(group))
        assertTrue(group.collapsed)
        files.forEach { assertFalse(editors.isFileOpen(it)) }

        PlatformTestUtil.waitForPromise(service.toggle(group)!!) // expand looks files up off the EDT
        assertFalse(group.collapsed)
        files.forEach { assertTrue(editors.isFileOpen(it)) }
    }
}
