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

    fun testOneGroupOpenAtATime() {
        val editors = FileEditorManager.getInstance(project)
        val (a, b, loose) = listOf("A2.txt", "B2.txt", "Loose.txt").map { myFixture.addFileToProject(it, "x").virtualFile }
        val service = TabGroupsService.getInstance(project)
        lateinit var first: TabGroup
        lateinit var second: TabGroup
        service.update {
            first = create("first").also { add(it, a.url) }
            second = create("second").also { add(it, b.url); it.collapsed = true }
        }
        listOf(a, loose).forEach { editors.openFile(it, false) }

        val settings = TabGroupsSettings.getInstance().state
        settings.exclusiveGroups = true
        try {
            PlatformTestUtil.waitForPromise(service.toggle(second)!!)
        } finally {
            settings.exclusiveGroups = false
        }
        assertTrue(first.collapsed)
        assertFalse(editors.isFileOpen(a)) // the previously open group's tab closed
        assertFalse(second.collapsed)
        assertTrue(editors.isFileOpen(b)) // the new group's tab opened
        assertTrue(editors.isFileOpen(loose)) // ungrouped tabs are untouched
    }
}
