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
}
