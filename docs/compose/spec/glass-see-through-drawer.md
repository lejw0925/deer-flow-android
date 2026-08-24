---
feature: glass-see-through-drawer
status: delivered
updated: 2026-08-25
branch: release/v1.2.0
commits: e58d847 (base; implementation uncommitted in working tree — pending user commit decision)
---

# See-through Liquid-Glass Drawer

## Report

**What was built** — The left drawer (compact modal drawer + expanded side panel) is now a
true see-through liquid-glass surface. The compact drawer samples the live screen content
behind it: `WorkspacePage` was moved INSIDE the shell's `layerBackdrop` box (alongside
`GeminiAuroraBackground`), so the drawer — a sibling outside that box — samples aurora + the
real conversation/page and renders it frosted-but-readable through the panel. The expanded
side panel (sitting at the screen's left edge, with no content behind it) keeps its
aurora-only recording and achieves see-through via the glass-treatment tuning alone. The
drawer panel + bottom bar glass calls were rewritten to the full liquid treatment the
interactive glass components already use: a low-alpha see-through tint (~0.20 light / ~0.28
dark panel; ~0.30/0.34 bottom bar) resolved per-theme from the glass tint tokens, a 6dp blur
(passed explicitly so the global 2dp `GlassTunables` is untouched), `Highlight.Ambient` edge
light-model layer, `useLens` chromatic-aberration edge refraction, and a strengthened
`glassEdge` (peakAlpha 0.40). The floating bottom bar also gets a soft `Shadow`. New
`internal` tuning constants `DrawerGlassBlurRadius`/`DrawerGlassEdgePeakAlpha` + per-site
tint helpers keep the see-through values local to the drawer. (Nit from review: promoted
`glassShadow()` to `internal` so the bottom bar reuses the single shadow definition.)

**Verification** — `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleDebug
--no-daemon --console=plain` → BUILD SUCCESSFUL (48s; 2 pre-existing warnings: a deprecated
`TabRow` and a redundant-`else` `when`, both unrelated to this change). `./gradlew
testDebugUnitTest lintDebug --no-daemon --console=plain` → BUILD SUCCESSFUL (2m41s, no new
failures). A fresh reviewer subagent confirmed spec compliance + correctness + codebase
consistency with NO Critical/Major issues (one Nit, addressed). The on-device visual
refraction CANNOT be verified on the headless swiftshader emulator (it does not render the
backdrop `RenderEffect` blur/lens) — the user will install on a real Honor phone (API 33+
for the lens) to confirm: chat visible frosted behind the compact drawer; edge refraction +
chromatic aberration at the rounded corners; bright directional edge highlight; bottom bar
floating with its shadow; expanded side panel transparent over the aurora; no scroll jank.

**Journey log**
- Root cause was architectural, not a missing modifier: the shell backdrop recorded only the
  aurora, so the drawer (shell-level glass) could never sample the chat behind it. The prior
  session had already wired `backdrop` into the drawer's `.glass()` calls — sampling worked,
  but the wrong (aurora-only) content was recorded.
- Key design nuance caught during planning: the expanded side panel sits at the screen edge
  with the chat to its right (not behind it), so recording the chat would not reveal it
  through the panel — it would only add per-frame cost during chat scroll. So only the
  compact layout records the page; the expanded layout stays aurora-only + glass-tuning.
- Risk carried (not a code defect, per review): nested `layerBackdrop`s (shell + per-screen,
  different instances) and whether `ModalNavigationDrawer`'s sheet is in-composition
  (backdrop-reachable) are sound-as-far-as-code-goes but UNVERIFIED at runtime — the
  per-screen `LocalGlassBackdrop` shadow is what makes nesting the page inside the shell's
  recorded box safe from self-sampling. First real-device build must confirm no crash / empty
  sample; fallback is a single shared shell backdrop (remove per-screen backdrops — bigger
  refactor).
- See-through tint is passed explicitly to the drawer calls only; the global `GlassTints` /
  `GlassTunables` were deliberately left untouched so the small interactive glass elements
  keep their tight 2dp focus.
- `Color.luminance()` keys off RGB only, so `veil.luminance() < 0.5f` reliably picks the
  dark/light branch for tints derived from `rememberGlassTints().veil` regardless of the
  `.copy(alpha=…)` scaling — the correct per-theme test here.
## [S1] Problem
The left drawer (and its bottom search bar) render as a flat translucent panel that only
samples the static `GeminiAuroraBackground` gradient. The user cannot see the live screen
content (conversation/page) behind the drawer — it should be true see-through liquid glass
with the content behind visible, frosted but clearly readable, plus pronounced glass-edge
refraction.

## [S2] Design

### Root cause
The shell backdrop (`rememberGlassBackdrop()` in `WorkspaceShell`) records **only**
`GeminiAuroraBackground`; the active `WorkspacePage` (→ ChatScreen/etc.) is a **sibling
outside** the recorded `Box` (`WorkspaceShell.kt:191-193` compact, `:221-223` expanded). Each
screen ALSO creates its own `rememberGlassBackdrop()` shadowing the shell's and recording
that screen's real content. The drawer is a shell-level glass element → it samples the shell
backdrop → sees only the aurora gradient, never the chat content behind it.

### Chosen behavior
1. **Record the live page into the shell backdrop — COMPACT layout only.** Move
   `WorkspacePage(...)` inside the shell's `Box(Modifier.fillMaxSize().layerBackdrop(backdrop))`
   as a sibling of `GeminiAuroraBackground` in `CompactWorkspace`. The modal drawer overlays
   the content, so the drawer (a sibling OUTSIDE the recorded layer — architecture rule: glass
   must not be inside the layer it samples) samples aurora + live page → reveals blurred chat
   behind it.
   - The EXPANDED side panel sits at the screen's left edge with the chat to its RIGHT (not
     behind it), so recording the chat would not reveal it through the panel — it would only
     add per-frame recording cost during chat scroll. Keep the expanded shell backdrop
     recording aurora-only; achieve the side panel's see-through via the glass-treatment
     tuning (below) over the aurora.
   - Nested `layerBackdrop`s (shell + per-screen) are **different instances**; the documented
     crash is same-instance re-record, which this avoids. Runtime behavior is device-verified.
2. **See-through glass treatment** for the drawer panel + bottom bar (both layouts):
   - tint lowered to ~0.20 (light) / ~0.28 (dark) white/black — well below the current `veil`
     (0.68/0.44) so content behind is clearly readable.
   - `blurRadius = 6.dp` (explicitly on the drawer calls; global `GlassTunables.BlurRadius`
     stays 2.dp so interactive glass is unaffected).
   - `useLens = true` (keeps lens + chromatic-aberration edge refraction, API 33+).
   - `highlight = { Highlight.Ambient }` — the ambient edge light-model layer the drawer
     currently omits (the full liquid treatment `GlassCard`/`GlassButton` use).
   - `glassEdge(drawerShape, peakAlpha = 0.40f)` — strengthened directional edge stroke for
     the requested "glass edge refraction" gleam.
   - Bottom bar additionally gets a soft `shadow` so it reads as a floating bar over the
     drawer glass; tint slightly stronger (~0.30/0.34) than the panel.
3. **No global tint/tunable changes.** The see-through tint is passed explicitly to the drawer
   `.glass()` calls only; `GlassTints`/`GlassTunables` are untouched (they tune every other
   glass surface). The bottom-bar `OutlinedTextField` stays Material3 — the container glass
   supplies the refractive look.

### Contracts
- `Modifier.glass(...)` (`Glass.kt:206`) already accepts `tint`, `blurRadius`, `highlight`,
  `shadow`, `useLens`, `chromaticAberration` — no API change needed.
- `Highlight.Ambient` (from `com.kyant.backdrop.highlight.Highlight`, already used by
  `GlassCard`) and `Shadow`/`InnerShadow` (from `com.kyant.backdrop.shadow`) are public.

### Out of scope
- Changing global `GlassTints` / `GlassTunables`.
- Removing/merging per-screen backdrops (would self-sample the screen glass).
- Drawer information-architecture / layout redesign.
- Making the `OutlinedTextField` itself glass.

## [S3] Out of scope
See [S2] Out of scope. Visual refraction (backdrop `RenderEffect` blur/lens) cannot be
verified on the headless swiftshader emulator — it MUST be checked on a real API 33+ device.

## Tasks
- [x] T1: Write feature spec doc — acceptance: this file exists at the path above (covers: S2)
- [x] T2: Record the live page into the shell backdrop (compact) + rewrite drawer/bottom-bar
  glass to the see-through liquid treatment (both layouts) — acceptance: compact drawer
  samples aurora+page (device render: chat visible frosted behind drawer); expanded side panel
  is transparent glass over aurora; drawer compiles & builds (covers: S2)
- [x] T3: Verify build (assembleDebug) + lint + unit tests — acceptance: assembleDebug succeeds;
  lint/test no new failures (PRE-EXISTING baseline failures marked) (covers: S2)
- [x] T4: Review — acceptance: fresh subagent confirms spec compliance + correctness + codebase
  consistency; critical findings fixed (covers: S2)
- [x] T5: Finalize — acceptance: spec status=delivered, commits/range recorded, user informed
  of the required device visual check (covers: S2)
