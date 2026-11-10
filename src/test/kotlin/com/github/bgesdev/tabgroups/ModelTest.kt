package com.github.bgesdev.tabgroups

import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xmlb.XmlSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ModelTest {
    private val a = "file:///p/A.kt"
    private val b = "file:///p/B.kt"

    @Test
    fun addMovesFileBetweenGroups() {
        val s = TabGroupsState()
        val g1 = s.create("one")
        val g2 = s.create("two")
        assertEquals(GROUP_COLORS[1], g2.color)

        s.add(g1, a)
        s.add(g1, a) // no duplicates
        assertEquals(listOf(a), g1.files)

        s.add(g2, a)
        assertEquals(emptyList<String>(), g1.files)
        assertSame(g2, s.groupOf(a))
    }

    @Test
    fun removeRenameDelete() {
        val s = TabGroupsState()
        val g = s.create("one").also { s.add(it, a); s.add(it, b) }

        s.remove(a)
        assertNull(s.groupOf(a))
        assertEquals(listOf(b), g.files)

        s.rename(g, "renamed")
        assertEquals("renamed", g.name)

        s.delete(g)
        assertEquals(0, s.groups.size)
        assertNull(s.groupOf(b))
    }

    @Test
    fun serializationRoundTrip() {
        val s = TabGroupsState()
        s.create("one").also { s.add(it, a); it.collapsed = true }
        s.create("two", "Rose").also { s.add(it, b) }

        val xml = XmlSerializer.serialize(s)
        val back = XmlSerializer.deserialize(xml, TabGroupsState::class.java)

        assertEquals(listOf("one", "two"), back.groups.map { it.name })
        assertEquals(listOf(GROUP_COLORS[0], "Rose"), back.groups.map { it.color })
        assertEquals(listOf(true, false), back.groups.map { it.collapsed })
        assertEquals(listOf(listOf(a), listOf(b)), back.groups.map { it.files })
        assertEquals(JDOMUtil.write(xml), JDOMUtil.write(XmlSerializer.serialize(back)))
    }

    @Test
    fun autoGroupRules() {
        val rules = listOf(AutoGroupRule("Kotlin", "kt, .kts"), AutoGroupRule("Views", "*.blade.php"), AutoGroupRule("PHP", "php"))
        assertEquals("Kotlin", matchRule(rules, "Main.KT"))
        assertEquals("Kotlin", matchRule(rules, "build.gradle.kts"))
        assertEquals("Views", matchRule(rules, "home.blade.php")) // longest extension wins
        assertEquals("PHP", matchRule(rules, "index.php"))
        assertNull(matchRule(rules, "notes.txt"))
        assertNull(matchRule(rules, "kt")) // a bare name is not an extension
        assertNull(matchRule(listOf(AutoGroupRule("", "txt")), "a.txt")) // rows without a group name are ignored
    }

    @Test
    fun tablesKeyword() {
        val rules = listOf(AutoGroupRule("SQL", "sql"), AutoGroupRule("DB", "@tables, csv"))
        assertEquals("DB", matchRule(rules, "users", isTable = true))
        assertNull(matchRule(listOf(AutoGroupRule("SQL", "sql")), "users", isTable = true)) // no @tables rule
        assertEquals("DB", matchRule(rules, "export.csv")) // normal extensions in the same row still work
        assertNull(matchRule(rules, "users.@tables")) // the keyword never matches a file name
        assertEquals("SQL", matchRule(rules, "schema.sql"))
    }

    @Test
    fun autoAddCreatesGroupOnceAndRespectsExistingGroups() {
        val s = TabGroupsState()
        val manual = s.create("manual").also { s.add(it, a) }

        assertEquals(false, s.autoAdd(a, "Kotlin")) // already grouped by hand: left alone
        assertSame(manual, s.groupOf(a))

        assertEquals(true, s.autoAdd(b, "Kotlin"))
        assertEquals(true, s.autoAdd("file:///p/C.kt", "Kotlin"))
        assertEquals(1, s.groups.count { it.name == "Kotlin" })
        assertEquals(listOf(b, "file:///p/C.kt"), s.groups.single { it.name == "Kotlin" }.files)
    }

    @Test
    fun settingsRoundTrip() {
        val data = TabGroupsSettings.Data(exclusiveGroups = true, autoRules = mutableListOf(AutoGroupRule("Kotlin", "kt, kts")))
        val back = XmlSerializer.deserialize(XmlSerializer.serialize(data), TabGroupsSettings.Data::class.java)
        assertEquals(true, back.exclusiveGroups)
        assertEquals(listOf(AutoGroupRule("Kotlin", "kt, kts")), back.autoRules)
    }
}
