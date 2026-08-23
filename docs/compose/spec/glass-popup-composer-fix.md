---
feature: glass-popup-composer-fix
status: verified
updated: 2026-08-24
branch: release/v1.1.8
commits: 4f334a2..HEAD
---

# Glass Popup & Composer Fix

## Report

## [S1] Problem

Three popup surfaces and the chat composer render without the liquid-glass look
the rest of the UI has:

- Model selector popup (top bar) — background is simply transparent.
- Mode / "thinking" selector popup (top bar) — same transparent background.
- Long-press conversation menu (workspace drawer) — same transparent background.
- Bottom composer (input box) — reads as dead-black in portrait; in landscape
  the blurred content behind it is faintly visible.

No blur, no frosted fill, no edge refraction on any of the popups.

## [S2] Design

### Popups cannot sample the activity backdrop

`GlassMenuSurface` (the body shared by `GlassDropdownMenu` and the slash-skill
`Popup`) currently calls `Modifier.glass(backdrop = LocalGlassBackdrop.current,
useLens = true, …)`. Compose `DropdownMenu`/`Popup` open in a separate window
whose composition inherits the parent's `CompositionLocal`s, so
`LocalGlassBackdrop.current` is **non-null** inside the popup. The `Backdrop`
records the activity's content layer via `Modifier.layerBackdrop`, but a popup
window's `RenderEffect` cannot sample pixels from another window's render tree.
`drawBackdrop` therefore runs, samples nothing, and only the faint `veil` tint
(`drawRect`) is drawn — producing the "simple transparent" look with no blur,
no lens, and no refraction.

The library cannot cross windows; the documented architecture (AGENTS.md) already
states popup-window surfaces must use `Modifier.glassFrosted` instead.
`glassFrosted` draws a strong translucent fill + a top-lit vertical sheen + a
gradient highlight border (the "edge refraction" look) without sampling, so it
works in any window. The fix is to make `GlassMenuSurface` use `glassFrosted` +
`glassEdge` and stop passing the inherited backdrop. This single change fixes
every popup site at once (model selector, mode selector, overflow menu,
conversation long-press, memory/tasks menus, slash-skill suggestions).

### Composer samples a dead-dark backdrop

`ChatScreen` records its content into a backdrop via
`Box(Modifier.fillMaxSize().layerBackdrop(backdrop))` but, unlike
`WorkspaceShell` and the login screen, it does **not** place
`GeminiAuroraBackground` inside that recorded box. `rememberGlassBackdrop()`
draws the solid background color first, then `drawContent()` (the message
list). The message list is given `bottomPadding = bottomOverlayHeight` so it
never scrolls under the composer; the region of the recorded layer directly
behind the composer is therefore just the solid dark background — which the
composer's glass samples as dead-black.

In landscape with the keyboard open, `.imePadding()` lifts the composer up over
the message list (whose bottom padding was measured without the IME), so the
glass samples real message content — the faint blurred text the user sees.

Fix: draw `GeminiAuroraBackground(Modifier.fillMaxSize())` as the first child
inside the recorded `layerBackdrop` box, matching `WorkspaceShell`. The aurora
glows are then always present behind the composer (and the top bar) and are
picked up by sampling glass, so the composer reads as liquid glass — dark with
colorful refraction — in every orientation instead of dead-black.

## [S3] Out of Scope

- The `useLens` chromatic-aberration effect is intentionally not restored on
  popups: the lens needs a sampled backdrop, which popups can never provide.
  `glassEdge` supplies the edge-refraction look without sampling.
- No change to the composer's live backdrop sampling; it stays a real
  `Modifier.glass` so content continues to refract through it.
- Other screens' aurora placement is unchanged (they already place it inside the
  recorded layer).

## Tasks

- [x] T1: Make `GlassMenuSurface` use `glassFrosted` + `glassEdge` (no backdrop
  sampling, no lens) — acceptance: model/mode/overflow/long-press/slash popups
  show a translucent frosted fill with a highlight edge, not transparent
  (covers: S2). _Verified: `GlassMenuSurface` (GlassMenu.kt:141) uses
  `glassFrosted` + `glassEdge`; comment documents the popup-window sampling
  limit._
- [x] T2: Place `GeminiAuroraBackground` inside ChatScreen's recorded
  `layerBackdrop` box — acceptance: composer shows colored aurora refraction
  in portrait, not dead-black (covers: S2). _Verified: ChatScreen.kt:256-261
  places `GeminiAuroraBackground` as first child of the recorded
  `layerBackdrop` box._
- [x] T3: `assembleDebug` + `lintDebug` pass — acceptance: clean build
  (covers: S2). _Verified: `BUILD SUCCESSFUL in 2m 14s`; lint 0 errors / 71
  warnings._
- [x] T4: Verify on the local emulator: screenshot each of the three popups and
  the composer — acceptance: frosted popups + non-black composer visible in
  captured frames (covers: S2; depends: T3). _Verified on `deerflow_api36`
  emulator (debug build). All three popups opened (Model / Run-mode / long-press
  Pin-Rename-Delete). Vision model unavailable, so verified via pixel stats:
  composer 0% pure-black with aurora color/variance; each popup surface
  brightens + desaturates the aurora behind it (e.g. sat 0.17→0.07, +19
  brightness) — the frosted-veil signature, not transparent. Frames in
  `.tmp/t4_0[1-4]_*.png`._
