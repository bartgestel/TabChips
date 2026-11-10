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

    /** Adds [url] to the group called [groupName] (creating it) unless the file is already in a group. */
    fun autoAdd(url: String, groupName: String): Boolean {
        if (groupOf(url) != null) return false
        val group = groups.find { it.name == groupName } ?: create(groupName)
        group.files += url
        return true
    }
}

/** Settings row: files whose name ends in one of [extensions] go into the group named [group] when opened. */
data class AutoGroupRule(var group: String = "", var extensions: String = "") {
    /** "kt, .kts *.blade.php" → [kt, kts, blade.php] */
    fun extensionList(): List<String> =
        extensions.split(',', ' ', ';').map { it.trim().removePrefix("*").removePrefix(".").lowercase() }.filter { it.isNotEmpty() }
}

/** Extensions-column keyword for tables and views opened from the Database tool window. */
const val TABLES_KEYWORD = "@tables"

/**
 * The group name for a file, or null. Longest extension wins, so "blade.php" beats "php".
 * [isTable] files (no extension) match only the [TABLES_KEYWORD] keyword.
 */
fun matchRule(rules: List<AutoGroupRule>, fileName: String, isTable: Boolean = false): String? {
    val name = fileName.lowercase()
    val candidates = rules.filter { it.group.isNotBlank() }
        .flatMap { rule -> rule.extensionList().map { it to rule.group.trim() } }
    if (isTable) return candidates.firstOrNull { (ext, _) -> ext == TABLES_KEYWORD }?.second
    return candidates
        .filter { (ext, _) -> ext != TABLES_KEYWORD && name.endsWith(".$ext") }
        .maxByOrNull { (ext, _) -> ext.length }
        ?.second
}
