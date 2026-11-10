# Tab Chips

Browser-style tab groups for IntelliJ-based IDEs (2026.2+, built and tested against PhpStorm 2026.2.3).
A strip of colored chips, one per group: click a chip to collapse the group (its tabs close) or expand it
(its tabs reopen). Groups are stored per project in `.idea/workspace.xml`, so they are not shared via VCS.

## Use

- **Create:** click `+` on the strip, or right-click an editor tab → *New Group from This Tab…*
- **Assign:** right-click a tab → *Add to Group ▸* / *Remove from Group*. A file belongs to at most one group.
- **Edit a group:** right-click its chip → *Rename…*, *Color ▸*, *Delete Group*.
- **Without a strip** (e.g. all tabs closed): *Window ▸ Tab Chips ▸ Collapse/Expand "…"*.
- **Auto-grouping:** *Settings ▸ Tools ▸ Tab Chips ▸ Auto-Grouping* table: a group name plus extensions
  (`kt, kts`, `*.blade.php`). When you open a file that isn't in a group, it joins the matching group, which is
  created if needed. The longest matching extension wins. Only applies when a file is opened.
  The keyword `@tables` matches tables and views opened from the Database tool window (they have no extension).
- **One group at a time:** *Settings ▸ Tools ▸ Tab Chips ▸ Only one group open at a time*. Expanding a group
  collapses the open one and closes its tabs; ungrouped tabs stay open. Off by default.
- **Placement:** *Settings ▸ Tools ▸ Tab Chips*. Default: a strip below each editor's tab row.
  Experimental: one shared strip above all tab rows.

## Develop

```bash
./gradlew runIde        # sandbox IDE; logs in .intellijPlatform/sandbox/*/log_runIde/idea.log
./gradlew test          # model, strip, collapse/expand, placement fallback
./gradlew verifyPlugin  # Plugin Verifier against the recommended IDEs
```

## Code

| File | What |
|---|---|
| `Model.kt` | `TabGroup` / `TabGroupsState`: plain data + mutations, no IDE dependencies |
| `TabGroupsService.kt` | Project service, single source of truth, persistence, collapse/expand, editor listener |
| `TabGroupStrip.kt` | The chip strip. Host-independent: it only reads the service and redraws on change |
| `StripHosts.kt` | `StripHost` with Host A (editor notification) and Host B (shared strip), fallback logic |
| `Actions.kt` | Tab context menu, chip menus, Window menu |
| `TabGroupsSettings.kt` | App-level setting + settings page |

## Publishing to JetBrains Marketplace

Plugin ID `com.github.bgesdev.tabgroups`, display name **Tab Chips** ("Tab Groups" is already taken on the
Marketplace). License: MIT (see `LICENSE`).

**One-time setup**

1. Source: https://github.com/bartgestel/TabChips (also linked in the plugin description). Enter it as the
   source URL on the Marketplace plugin page.
2. Create a signing key and certificate, outside this repo (`*.pem` / `*.crt` are git-ignored anyway):
   ```bash
   openssl genpkey -aes-256-cbc -algorithm RSA -out private_encrypted.pem -pkeyopt rsa_keygen_bits:4096
   openssl rsa -in private_encrypted.pem -out private.pem
   openssl req -key private.pem -new -x509 -days 365 -out chain.crt
   ```
3. Export them for Gradle (the build reads these environment variables, see `build.gradle.kts`):
   ```bash
   export CERTIFICATE_CHAIN="$(cat chain.crt)"
   export PRIVATE_KEY="$(cat private_encrypted.pem)"
   export PRIVATE_KEY_PASSWORD='the password you chose'
   ```

**First release (manual upload)**

1. Bump `version` in `gradle.properties` and add a matching entry to `<change-notes>` in `plugin.xml`.
2. `./gradlew signPlugin verifyPlugin`: the signed zip is `build/distributions/TabGroups-<version>-signed.zip`.
   Install it with *Install Plugin from Disk* and try it.
3. Sign in at plugins.jetbrains.com, open *Add new plugin*, accept the Developer Agreement, choose MIT,
   upload the signed zip, and fill in the source URL. JetBrains reviews it; if there is no answer after
   3–4 working days, email marketplace@jetbrains.com.

**Later releases**

1. Create a token under *My Tokens* on the Marketplace and `export PUBLISH_TOKEN=...`.
2. Bump `version`, add change notes, then `./gradlew publishPlugin` (signs, then uploads).

## Plugin Verifier (`./gradlew verifyPlugin`)

| IDE | Result |
|---|---|
| PS-262.11879.104 (2026.2) | Compatible, no problems |
| PS-263.7307.215 (2026.3) | Compatible, 2 deprecated API usages |

**Deprecated (263): `FileEditorManager.openFile(VirtualFile, boolean)`**, used in `TabGroupsService.toggle`
and `TabGroupStrip.fileChip`. 263 suggests `requestOpenFile(file, FileEditorOpenRequest)`. That cannot be used
yet: 262 has no `FileEditorOpenRequest`, and `requestOpenFile(VirtualFile)` returns `void` in 262 but a
`CompletableFuture` in 263, so code compiled against one breaks on the other. The deprecated method still
works in 263. Switch when `since-build` moves to 263.

## Host B: what it relies on

The verifier reports no internal API, because none of the calls is annotated `@ApiStatus.Internal`. Host B still
depends on IDE internals that can change in any release, which is why it is off by default:

- `FileEditorManagerEx.getSplitters()` returns `EditorsSplitters` from an `impl` package.
- It assumes the editor area is the inner component of the frame's `ThreeComponentsSplitter`, and swaps in a
  wrapper panel (strip on top, editor area below).
- In Islands themes it reads `Island.arc` and paints its background through `IdeBackgroundUtil.withFrameBackground`
  so the window gradient shows around the rounded corners.

If the layout is not as expected, attaching throws, or the IDE later replaces the wrapper (for example in
Distraction Free mode), Host B logs a warning and switches to Host A automatically.

## Known limitations

- Renaming or moving a file drops it from its group (groups store URLs). Deleted files show greyed out.
- Opening a file from a collapsed group by hand does not expand the group; closing a tab does not remove it
  from its group.
- UI strings are not localized (no message bundle).
