package com.github.bgesdev.tabgroups

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import javax.swing.JLabel

class StripTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        TabGroupsService.getInstance(project).loadState(TabGroupsState()) // light project is shared between tests
    }

    fun testStripRendersGroupsAndSurvivesMissingFiles() {
        val file = myFixture.configureByText("A.txt", "x").virtualFile
        val service = TabGroupsService.getInstance(project)
        service.update {
            val g = create("one")
            add(g, file.url)
            add(g, "file:///does/not/exist/B.txt")
        }
        val strip = TabGroupStrip(project)
        strip.addNotify()
        try {
            assertEquals(listOf("one", "A.txt", "B.txt", "+"), strip.labels())

            service.update { groups[0].collapsed = true } // strip listens to the service
            assertEquals(listOf("one (2)", "+"), strip.labels())
        } finally {
            strip.removeNotify()
        }
    }

    private fun TabGroupStrip.labels() = components.map { (it as JLabel).text }
}
