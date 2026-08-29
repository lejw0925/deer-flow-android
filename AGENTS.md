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

## Session Handoff (2026-08-29)

Branch `release/v1.2.0`. The liquid-glass consistency round is COMMITTED:
`b7b8ad0` (feat(ui): floating headers, frosted cards, material unification —
supersedes the previously uncommitted 2026-08-28 rounds), `cbb1c0c` (fix(tools):
mock gateway artifact fixtures by basename), `a7d04f2` (test: regenerate
workspace screenshot baselines). See `~/.config` project MEMORY for durable
env notes (Clash proxy, release signing, github/Maven TLS + aliyun mirror,
popup-glass constraint). Everything below is context for the next agent.

### What the consistency round changed (design rules now in force)

- **Headers**: `FloatingScreenTopBar` (SharedComponents.kt) = glass circular
  `GlassIconButton`s + floating title, ChatScreen style. **`GlassTopAppBar` is
  DELETED** — all screens (tasks/memory/profile/about/licenses/agents/SSO)
  migrated; screens still measure the header via `onGloballyPositioned` and pad
  their scrollables by that height. SSO screen now has an aurora backdrop
  (recorded art layer only — the WebView is intentionally NOT recorded) and a
  floating header; `LoadingScreen` has an aurora layer.
- **Lists**: ProfileScreen rewritten as grouped `SettingsCard` frosted panels
  (one card per section, transparent ListItems inside, `SettingsRowDivider`
  between rows; both artifact-limit sliders live INSIDE the storage card per
  user decision). Memory summary/fact rows and Task rows are
  `glassFrosted(shapes.medium)` cards with `spacedBy(8.dp)`; memory row
  dividers removed. Drawer selected thread = frosted pill with
  `primary.copy(alpha=0.22f)` veil (`SelectedThreadShape` 12dp); offline
  banners are frosted cards (memory + drawer).
- **Buttons role rule**: sheet/dialog primary = `Button`, secondary =
  `OutlinedButton`/`TextButton`, destructive confirm = error color.
  `FilledTonalButton` eliminated everywhere (Lark install/config/auth →
  Button, open-verification → Outlined; Channels connect → Button; HumanInput
  submit → Button; Agent detail set-default → Outlined). `GlassButton` stays
  for sampling-capable floating layers (login) only.
- **Chat**: composer TextField containers `Color.Transparent` (sits directly
  on the glass panel); TodoSummary/TodoProgressDetails upgraded to real
  sampling glass (they are siblings OUTSIDE the recorded layer); progress
  track unified to `secondary.copy(alpha=0.18f)`; message list + composer
  widths unified at 900dp; welcome centers between the two overlays.
- **Menus**: explicit `RoundedCornerShape(20.dp)` overrides removed —
  `GlassDropdownMenu` default 24dp everywhere (matches sheets' 28dp top
  corners and `GlassMenuSurface`).
- Trailing action buttons inside list rows stay raw M3 `IconButton` (they sit
  inside the recorded layer; see SEGV rule). AttachmentChip's 32dp mini
  buttons stay raw too.

### Verification status (2026-08-29, emulator-5554 API 36)

- Full `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`:
  BUILD SUCCESSFUL.
- Targeted `connectedDebugAndroidTest` (WorkspaceNavigation, MemoryScreen,
  ChatControls, AgentsScreen, TasksScreen, ProfileScreen, SsoLoginControls,
  BrowserLiveSheet, AuroraAnimationProbe, Accessibility): **82 tests, 81 green**;
  the only failure is the pre-existing RED `runModeMenuDispatchesSupportedMode`
  (ChatControlsTest.kt:120, fails on HEAD without this round too).
  AccessibilityTest 6/6 → the 48dp `GlassIconButton` touch-target a11y fix is
  CONFIRMED.
- `WorkspaceScreenshotTest` baselines regenerated on this emulator
  (`-e deerflow.record_screenshots true`, `animator_duration_scale 1`) and
  verified 6/6 against the new in-code signatures.
- NOT yet verified: real-device pass (iQOO `V2520A` and Honor `APH-AN00`) —
  neither was reachable this session. iQOO adb id contains spaces/parens —
  `"adb-10AFBD2D6C0042F-6slL6P (2)._adb-tls-connect._tcp"` — always quote it.

### Durable lessons (kept from the 08-28 rounds; still critical)

- **SEGV rule (crash-proven): NEVER use real-sampling `Modifier.glass` inside
  the `layerBackdrop` recorded layer** — libhwui `computeTransformImpl`
  infinite recursion → RenderThread SIGSEGV on real devices (tasks empty-state
  button was the original offender; emulator mock gateway always returns 2
  tasks so it never showed the empty state). Inside the recorded layer use
  `glassFrosted` only.
- **AgentRow swipe**: SwipeToDismissBox was replaced with a two-anchor
  `AnchoredDraggableState<AgentRevealValue>` (Settled=0 / Revealed=
  -actionsWidthPx). Two bytecode-verified defects forced this:
  `AnchoredDraggableState.progress` returns 1f at rest when
  settledValue==targetValue (so `progress > 0f` does NOT hide swipe actions —
  they ghost through frosted cards), and its EndToStart anchor is the FULL row
  width (released swipes slid the card off-screen). Actions render only while
  `offset < -1f`; tap on the shifted card closes.
- Aurora/tints/blur rationale and the round-1 regression tests
  (SharedComponentsTest compact-time cases, ChatControlsTest overflow,
  BrowserLiveSheetTest retry, MemoryScreenTest summary detail with
  `useUnmergedTree`, AuroraAnimationProbeTest) are described in commit
  `b7b8ad0`'s message. Compose gotchas: `assertDoesNotExist`/`onNode` are
  member fns — do NOT import them; `captureToImage()` returns ImageBitmap →
  `asAndroidBitmap().sameAs()`.

### Emulator facts that contradict older notes

- **This API36 swiftshader AVD DOES render backdrop blur** — drawer/menu
  screenshots show real background smearing; in-composition menus verified
  sampling (conversation ⋮ menu ghosts the messages behind it). The old
  "swiftshader can't render RenderEffect" note is wrong for this AVD.
- BUT it is brutally slow at it: first-launch/tap frames take 5–8s → repeated
  ANR dialogs (main thread parked in `HardwareRenderer.setStopped` waiting on
  RenderThread stuck in `glCreateProgram` over the qemu pipe). The app DOES
  finish each frame; tap Wait and continue. Login-by-typing is impractical;
  seed the session instead: force-stop, then an instrumentation snippet that
  runs `SettingsStore.setServerUrl("http://10.0.2.2:2027")` +
  `CookieManager.setCookie(url, "access_token=emulator; Path=/; HttpOnly")` +
  `flush()` (mock gateway accepts it; instrumentation component is
  `com.deerflow.mobile.test/androidx.test.runner.AndroidJUnitRunner` — note the
  `.test` package). A `SeedSessionTest` doing exactly this was used and then
  deleted; recreate it if needed.
- **Aurora animation DOES tick on this emulator — the earlier "static"
  measurement was self-inflicted**: `rememberInfiniteTransition` respects the
  system `animator_duration_scale` developer setting (verified in the
  animation-core 1.11.4 bytecode: `InfiniteTransition.run` reads the scale and
  suspends its frame loop at 0). The session had zeroed the animation scales
  to fight ANRs and never restored them. With `animator_duration_scale 1` the
  idle app sits at ~100% CPU driving ~0.2fps frames (each frame is ~5s of
  swiftshader GPU work), and screencaps 20s apart show the aurora clearly
  drifted/breathed (~77% sampled pixels changed, no interaction). Correctness
  proven on-device; only smoothness needs a real GPU. NOTE: a compose-test
  harness probe (createComposeRule, autoAdvance, no interaction) still sees a
  static image — the test environment's `InfiniteAnimationPolicy` gates
  infinite animations; do not trust the harness for this, use the real app.
- Helper scripts for manual walkthroughs live in `.tmp/glass-check/`
  (`walk.py`: texts/tap-text/screenshot; `login_fill.py`: focus-validated
  field fill).

### Kept from before: real liquid glass on popups (#1) — in-composition menu hosting

(Details from the previous round preserved; still accurate.)

- `GlassMenuHostState` + `LocalGlassMenuHost` in `ui/glass/GlassMenuHost.kt`;
  screens provide it next to `LocalGlassBackdrop` and place ONE
  `GlassMenuOverlayHost(state)` as the LAST sibling inside the provider
  (ChatScreen, WorkspaceShell, TasksScreen, MemoryScreen).
- `GlassDropdownMenu` with a host → hosted in-composition panel anchored to the
  call-site parent bounds; without a host → popup+frosted fallback.
- `GlassMenuSurface` picks `Modifier.glass` vs `glassFrosted` via
  **`LocalGlassMenuInComposition`** (true only around the overlay panel) — never
  gate on `LocalGlassBackdrop` (non-null-but-unreachable in popup windows).

Known REDs (documented earlier, live in classes outside the 08-29 targeted
run — re-verify when touching those areas, NOT caused by the glass round):
`citationTapBringsItsMatchingSourceIntoView`, 5× `WorkspaceDatabaseMigration…`
(kotlinx `AbstractMethodError`), `SettingsStore` snapshot mismatch, and
`runModeMenuDispatchesSupportedMode` (reconfirmed 08-29 on the emulator).
Screenshot baselines are current as of `a7d04f2` — regenerate with
`-e deerflow.record_screenshots true` only for intentional visual changes,
with `animator_duration_scale 1` (at 0 the aurora freezes mid-frame and
pollutes the capture).

> Still pending: real-device pass (iQOO + Honor) on the glass round — glass
> blur look at 60fps, aurora breathing smoothness, edge refraction, frosted
> card rows/sections. Animation correctness and the full Compose test surface
> are already verified on the emulator.
