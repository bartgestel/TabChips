package com.github.bgesdev.tabgroups

/** Names of the platform's theme-aware file colors (see FileColorManager). */
val GROUP_COLORS = listOf("Blue", "Green", "Orange", "Rose", "Violet", "Yellow", "Gray")

/** One group. Files are stored as VirtualFile URLs so missing files survive a restart. */
class TabGroup(
    var name: String = "",
    var color: String = GROUP_COLORS.first(),
    var collapsed: Boolean = false,
    var files: MutableList<String> = mutableListOf(),
)

/** All groups of a project. Plain data plus mutations, so it is testable without the IDE. */
class TabGroupsState(var groups: MutableList<TabGroup> = mutableListOf()) {

    fun groupOf(url: String): TabGroup? = groups.find { url in it.files }

    fun create(name: String, color: String = GROUP_COLORS[groups.size % GROUP_COLORS.size]): TabGroup =
        TabGroup(name, color).also { groups += it }

    /** A file belongs to at most one group, like browser tabs; adding moves it. */
    fun add(group: TabGroup, url: String) {
        remove(url)
        group.files += url
    }

    fun remove(url: String) = groups.forEach { it.files.remove(url) }

    fun rename(group: TabGroup, name: String) { group.name = name }

    fun delete(group: TabGroup) { groups.removeIf { it === group } }
}
