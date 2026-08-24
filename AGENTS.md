# Repository Guidelines

## Project Structure & Architecture

This is a single-module Kotlin Android client. Production code is under
`app/src/main/java/com/deerflow/mobile/`: `data/` contains Gateway, Room, and
DataStore code; `run/` owns active-run coordination and notifications; and
`ui/` contains Compose screens, presentation helpers, and themes. Within `ui/`,
`glass/` is the liquid-glass design system built on Kyant0's Backdrop library
(`io.github.kyant0:backdrop`, pinned to 1.0.6 — the 2.0.0 AAR requires
compileSdk 37 / AGP 9). Glass architecture rule: a screen records its
scrollable content into a backdrop via `Modifier.layerBackdrop(backdrop)` +
`rememberGlassBackdrop()`, and glass elements (`Modifier.glass(...)`,
`GlassTopAppBar`, `GlassModalBottomSheet`, …) must be siblings OUTSIDE that
recorded layer — never inside it (self-sampling smears) and never recorded
again (crash). Rows/cards inside the recorded layer use `Modifier.glassFrosted` instead.
Popup-window surfaces (DropdownMenu, Popup) also use `glassFrosted` UNLESS the
menu renders in-composition: `GlassDropdownMenu` routes through the screen's
`GlassMenuOverlayHost` (see `ui/glass/GlassMenuHost.kt`) when a
`LocalGlassMenuHost` is provided, giving real backdrop-sampled glass in the
activity window; `GlassMenuSurface` picks real `glass` vs `glassFrosted` via
`LocalGlassMenuInComposition` (true ONLY around the overlay panel — never trust
`LocalGlassBackdrop` for this, it is non-null-but-unreachable inside popup
windows). Screens with menus must place ONE `GlassMenuOverlayHost` as the last
sibling inside their `LocalGlassBackdrop` provider (done in ChatScreen,
WorkspaceShell, TasksScreen, MemoryScreen). Effects render on
API 31+ (lens 33+); below that `glass()` falls back to a translucent fill.
Android
resources are in `app/src/main/res/`, and checked-in Room migration schemas are
in `app/schemas/`. Keep protocol, persistence, and UI changes in their
respective layers. `tools/mock_gateway.py` provides a local Gateway fixture.

## Build, Test, and Development Commands

Use JDK 17 and the checked-in Gradle wrapper. From the repository root:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --no-daemon --console=plain
```

This runs local tests and lint, then creates the debug app and instrumentation
test APKs. The app APK is `app/build/outputs/apk/debug/app-debug.apk`. Run a
focused local test with `./gradlew :app:testDebugUnitTest --tests
'com.deerflow.mobile.data.SseParserTest'`. For device tests, select an emulator
explicitly: `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest`.
Start `python3 tools/mock_gateway.py --port 2027` for a self-contained local
Gateway.

## Coding Style and Naming

Follow `kotlin.code.style=official`: four-space indentation, idiomatic Kotlin,
and small, focused functions. Use `PascalCase` for classes and composables,
`camelCase` for functions and properties, and `lower_snake_case` for resource
names such as `notification_run_terminal.xml`. Keep Compose state and rendering
in `ui/`; do not make screens call Retrofit or Room directly. Run `lintDebug`;
this repository has no separate formatter or ktlint task.

## Testing Guidelines

Place JVM tests in `app/src/test/` and emulator/Compose/Room tests in
`app/src/androidTest/`. Name test classes `*Test.kt` and keep their package
aligned with the code under test. Add focused regression coverage for changes
to SSE reduction, caching, migrations, Gateway contracts, or UI controls.
Run the relevant local test first, then the full build command above; use
instrumentation tests for Android framework, database, or Compose behavior.

## Commits, Pull Requests, and Configuration

The existing history uses Conventional Commit-style subjects, for example
`feat: initialize DeerFlow Android client`; use concise imperative subjects such
as `fix: preserve partial stream updates`. Keep commits scoped. Pull requests
should state the user-visible behavior, tests run, Gateway-contract impact, and
include screenshots for visual changes. Never commit `local.properties`,
`keystore.properties`, credentials, or release signing material. Debug builds
may use local HTTP endpoints; release deployments require appropriate HTTPS and
signing configuration.

## Session Handoff (2026-08-24)

Branch `release/v1.1.8`. HEAD `31f7c08` (pushed; beta `v1.1.8-beta.2` published
with that commit's release-signed APK). See `~/.config` project MEMORY for the
full durable notes (Clash proxy node switching, release signing, the
github.com/Maven TLS block + aliyun mirror workaround, the #1 popup-glass
constraint). This section is the in-flight work for the next agent.

### Uncommitted working-tree changes (build-verified `assembleDebug` ✅, not committed)

Three files, all chat/glass tuning per user feedback this round:

- `ui/ChatScreen.kt` — **Top bar: model selector is now the compressible element.**
  Removed the `Spacer(weight 1f)`; `ChatTopSelectors` gets `Modifier.weight(1f, fill=false)`
  (new `modifier` param threaded to its `Row`); the model `TopSelector` gets
  `Modifier.weight(1f, fill=false).widthIn(max=152.dp)` so it shrinks + ellipsizes
  when crowded while nav/mode/browser/overflow stay fixed. `TopSelector` now applies
  the caller `modifier` to its **outer `Box`** (was the inner `Surface` — weight was
  ignored there) and the `Surface` uses `Modifier.fillMaxWidth()`.
  Also: composer `Column` padding `start 12.dp -> 6.dp` (left margin was larger
  than top/bottom; user wanted it smaller).
- `ui/MessageContent.kt` — **Conversation content no longer uses glass/gradient.**
  Message bubbles: `Surface(color=…)` by role instead of `Modifier.glassFrosted`
  (`User -> primaryContainer`, `Tool/System -> surfaceContainerHigh`,
  `Assistant -> Transparent`). `ProcessingCard` (thinking block) and
  `HumanInputCard` -> `surfaceContainerHigh` (was `glassFrosted`). The
  `import …glassFrosted` is now unused in this file — remove it when convenient.
- `ui/glass/Glass.kt` — `GlassTunables.BlurRadius 6.dp -> 4.dp` (further blur
  reduction); **removed `vibrancy()`** from `Modifier.glass`'s effects (user wanted
  the color-mixing weakened; the backdrop library's `vibrancy()` has NO strength
  parameter — `void vibrancy(BackdropEffectScope)` — so the only way to weaken it is
  to drop it; sampled content now renders at natural saturation). The
  `import …effects.vibrancy` was removed too.

> These are visual glass/markdown tweaks; verify on a **real device** (the
> headless `swiftshader` emulator does NOT render the backdrop `RenderEffect`
> blur, so glass-blur changes can't be visually confirmed there).

### Done: Markdown rendering fix (user reported: headings too big, tables missing internal divider lines, tables all left-aligned)

Fixed in `ui/MarkdownContent.kt` (unit tests pass, incl. new
`ui/MarkdownTableAlignmentTest.kt`):

1. **Smaller headings**: `EnhancedMarkdown` now gets
   `typography = markdownTypography(h1..h6 = …)` — h1 `headlineSmall`, h2
   `titleLarge`, h3 `titleMedium`, h4–h6 `titleSmall`, all `SemiBold`, matching
   the legacy renderer's heading scale (m3 defaults were `displayLarge` etc.).
   Note the m3 API is `markdownTypography(h1: TextStyle, …)` (NOT
   `markdownStyle`) — confirmed from the v0.28.0 source at
   `multiplatform-markdown-renderer-m3/.../m3/MarkdownTypography.kt`.
2. **Table borders + alignment**: `borderedTableComponent` overrides the `table`
   slot in both `defaultMarkdownComponents` and `streamingMarkdownComponents`
   (streaming wraps it in `revealOnAppear`). `EnhancedMarkdownTable`/
   `EnhancedMarkdownTableRow` are adapted from the library default but draw an
   outer border + per-row `HorizontalDivider`s + per-cell vertical dividers
   (0.5.dp `dividerColor`), and `tableColumnAlignments(...)` parses the
   `TABLE_SEPARATOR` source (`:---` / `:---:` / `---:`) into per-column
   `TextAlign` passed to `MarkdownBasicText`. Cells are fixed 160.dp with
   horizontal scroll and wrap (no ellipsis). Key API facts: `MarkdownComponent =
   @Composable ColumnScope.(MarkdownComponentModel) -> Unit`; the model carries
   `content`/`node`/`typography`; `org.jetbrains:markdown` (ASTNode,
   GFMElementTypes/GFMTokenTypes) is an `api` dep of the renderer so it's on the
   compile classpath; `LocalMarkdownColors`/`LocalMarkdownDimens`/
   `MarkdownBasicText`/`buildMarkdownAnnotatedString` are all public.
3. Also removed the now-unused `import …glass.glassFrosted` from
   `MessageContent.kt`.

> Same caveat as above: visual check (heading scale, hairline cell borders,
> column alignment) should happen on a real device.

### Done: real liquid glass on popups (#1) — in-composition menu hosting

Implemented the approach the MEMORY note prescribed (NEW
`ui/glass/GlassMenuHost.kt`, compiles + connected-tested on emulator):

- `GlassMenuHostState` + `LocalGlassMenuHost`; screens provide it next to
  `LocalGlassBackdrop` and place ONE `GlassMenuOverlayHost(state)` as the LAST
  sibling inside the provider (done in `ChatScreen`, `WorkspaceShell` (covers
  the drawer), `TasksScreen`, `MemoryScreen`). Screens with their own backdrop
  MUST shadow the host so menus sample their own recorded layer.
- `GlassDropdownMenu` with a host → `HostedGlassDropdownMenu`: zero-size
  `Spacer` at the call site whose **parent** layout bounds anchor the panel
  (mirrors DropdownMenu), `BackHandler` for back, DisposableEffect open/close;
  without a host → unchanged popup+frosted fallback (isolated tests, slash
  popup `ChatScreen` ~1690 stays a `Popup(focusable=false)` + frosted).
- `GlassMenuSurface` picks `Modifier.glass` vs `glassFrosted` via
  **`LocalGlassMenuInComposition`** (true only around the overlay panel) — never
  gate on `LocalGlassBackdrop` (non-null-but-unreachable in popup windows →
  menu renders nothing; that was the original #1 regression).
- Overlay host: modal scrim (clickable, blocks scroll-through) + panel placed
  below/start-aligned with end-align + above-flip clamping, 140ms fade/scale-in
  (transformOrigin flips when placed above).
- Gotchas hit: `Modifier.offset { }` needs `foundation.layout.offset` import;
  entry/content/shape stored in `mutableStateOf` so overlay recomposes with the
  call site; anchor bounds written only when changed.
- Bonus fix while debugging: `EnhancedMarkdownTable` cell text is now trimmed
  (GFM CELL nodes keep a trailing space → broke exact `onNodeWithText`;
  `finalTableReplyPlacesTheReasoningControlAboveTheTable` was RED at HEAD and
  passes now), and `TopSelector` gained a `buttonModifier` param — tag +
  traversalIndex moved back onto the inner `Surface` (merged semantics), fixing
  `nonThinkingModelExposesFlashOnly`.

Pre-existing RED at HEAD `31f7c08` (NOT from this work, verified via a
`git worktree` HEAD build): `citationTapBringsItsMatchingSourceIntoView`,
the 44dp `GlassIconButton` vs 48dp a11y gate, `WorkspaceDatabaseMigration…`
(kotlinx `AbstractMethodError`), `SettingsStore` snapshot mismatch. The 6
`WorkspaceScreenshotTest` baselines differ after this round's deliberate
glass/markdown visual tuning — need intentional baseline regeneration.

> Glass blur on the emulator is NOT renderable (swiftshader, no RenderEffect) —
> the liquid look of in-composition menus still needs a real-device check.
