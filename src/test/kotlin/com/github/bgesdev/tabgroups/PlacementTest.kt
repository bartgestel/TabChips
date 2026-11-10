package com.github.bgesdev.tabgroups

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PlacementTest : BasePlatformTestCase() {
    fun testSharedStripFallsBackToPerEditorStrip() {
        val settings = TabGroupsSettings.getInstance().state
        settings.sharedStrip = true
        try {
            // Headless: the editor area has no frame parent, so Host B must refuse and Host A stays active.
            StripPlacement.getInstance(project).apply()
            assertInstanceOf(StripPlacement.getInstance(project).host, NotificationStripHost::class.java)
        } finally {
            settings.sharedStrip = false
        }
    }
}
