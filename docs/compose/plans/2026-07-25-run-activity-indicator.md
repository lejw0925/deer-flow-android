# Run Activity Indicator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align Android conversation UI with Web `RunActivity`: show a run-level “Working…” row with live elapsed time while a run is active, without replacing existing tool/thinking loaders.

**Architecture:** Keep tool-level and Thinking indicators as-is. Add a pure duration formatter (mirror Web `formatRunDuration`), stamp `startedAtEpochMs` on `RunState` when a run becomes active, and render a small `RunActivityRow` in the chat list footer when `state.run.active` for the open thread. Optionally surface completed duration from assistant `turn_duration` in a follow-up task if already present on messages; do not invent backend fields.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3 (`LoadingIndicator` / clock icon), existing `RunState` / `ChatScreen` / `MessageContent`, unit tests under `app/src/test/`.

---

## File map

| File | Responsibility |
|------|----------------|
| `app/src/main/java/com/deerflow/mobile/ui/RunDurationFormat.kt` | Pure `formatRunDuration(seconds)` + labels interface |
| `app/src/test/java/com/deerflow/mobile/ui/RunDurationFormatTest.kt` | Unit tests for formatter |
| `app/src/main/java/com/deerflow/mobile/data/Models.kt` | `RunState.startedAtEpochMs: Long?` |
| `app/src/main/java/com/deerflow/mobile/run/RunCoordinator.kt` | Set/clear `startedAtEpochMs` on connect/stream/stop |
| `app/src/main/java/com/deerflow/mobile/ui/RunActivityRow.kt` | Compose row: indicator + working text + `(elapsed)` |
| `app/src/main/java/com/deerflow/mobile/ui/ChatScreen.kt` | Show `RunActivityRow` when conversation run is active |
| `app/src/main/res/values/strings.xml` | EN strings for working / duration parts |
| `app/src/main/res/values-zh-rCN/strings.xml` | ZH strings |
| `app/src/androidTest/.../ConversationMessageListTest.kt` or new Compose test | Optional: row visible when `runActive` |

**Out of scope (YAGNI this plan):** Shimmer animation, notification chip redesign, smooth markdown streaming, completed “Completed in Xs” unless Task 6 finds existing `turn_duration` already parsed.

---

### Task 1: Duration formatter (TDD)

**Covers:** format parity with Web `formatRunDuration`

**Files:**
- Create: `app/src/main/java/com/deerflow/mobile/ui/RunDurationFormat.kt`
- Create: `app/src/test/java/com/deerflow/mobile/ui/RunDurationFormatTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
// app/src/test/java/com/deerflow/mobile/ui/RunDurationFormatTest.kt
package com.deerflow.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunDurationFormatTest {
    private val labels = RunDurationLabels(
        lessThanSecond = "<1s",
        hours = { h -> "${h}h" },
        minutes = { m -> "${m}m" },
        seconds = { s -> "${s}s" },
        separator = " ",
    )

    @Test fun zeroIsLessThanSecond() {
        assertEquals("<1s", formatRunDuration(0, labels))
    }

    @Test fun negativeOrInvalidIsNull() {
        assertNull(formatRunDuration(-1, labels))
    }

    @Test fun formatsHoursMinutesSeconds() {
        assertEquals("1h 2m 3s", formatRunDuration(3723, labels))
    }

    @Test fun omitsZeroUnits() {
        assertEquals("2m", formatRunDuration(120, labels))
        assertEquals("45s", formatRunDuration(45, labels))
    }
}
```

- [ ] **Step 2: Run tests — expect FAIL (unresolved reference)**

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:testDebugUnitTest --tests 'com.deerflow.mobile.ui.RunDurationFormatTest' --no-daemon --console=plain
```

- [ ] **Step 3: Minimal implementation**

```kotlin
// app/src/main/java/com/deerflow/mobile/ui/RunDurationFormat.kt
package com.deerflow.mobile.ui

data class RunDurationLabels(
    val lessThanSecond: String,
    val hours: (Int) -> String,
    val minutes: (Int) -> String,
    val seconds: (Int) -> String,
    val separator: String,
)

/** Mirrors web formatRunDuration: floor non-negative seconds; skip zero units. */
fun formatRunDuration(value: Int, labels: RunDurationLabels): String? {
    if (value < 0) return null
    if (value == 0) return labels.lessThanSecond
    val hours = value / 3600
    val minutes = (value % 3600) / 60
    val seconds = value % 60
    val parts = buildList {
        if (hours > 0) add(labels.hours(hours))
        if (minutes > 0) add(labels.minutes(minutes))
        if (seconds > 0) add(labels.seconds(seconds))
    }
    return parts.joinToString(labels.separator).ifEmpty { null }
}
```

- [ ] **Step 4: Run tests — expect PASS**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/deerflow/mobile/ui/RunDurationFormat.kt \
  app/src/test/java/com/deerflow/mobile/ui/RunDurationFormatTest.kt
git commit -m "feat: add run duration formatter for activity row"
```

---

### Task 2: Stamp run start time on RunState

**Covers:** live elapsed for active runs

**Files:**
- Modify: `app/src/main/java/com/deerflow/mobile/data/Models.kt` (`RunState`)
- Modify: `app/src/main/java/com/deerflow/mobile/run/RunCoordinator.kt` (all places that set `RunStatus.Connecting` / `Streaming` / clear `RunState()`)
- Check cache codec if `RunState` is serialized to Room — if yes, add optional field with default `null` (no migration if column is JSON blob)

- [ ] **Step 1: Extend RunState**

```kotlin
data class RunState(
    val status: RunStatus = RunStatus.Idle,
    val runId: String? = null,
    val lastEventId: String? = null,
    val reconnectAttempt: Int = 0,
    val clientMessageId: String? = null,
    val gatewayStatus: GatewayRunStatus = GatewayRunStatus.Unknown,
    /** Wall-clock start of the current local active run; null when idle. */
    val startedAtEpochMs: Long? = null,
) {
    val active: Boolean get() = status in setOf(
        RunStatus.Connecting, RunStatus.Streaming, RunStatus.Reconnecting, RunStatus.Stopping,
    )
}
```

- [ ] **Step 2: Set start when becoming active**

When creating initial active run (send / connect stream), if `startedAtEpochMs == null`, set `System.currentTimeMillis()`.

On resume/reconnect of the **same** active run, **preserve** existing `startedAtEpochMs` (do not reset).

On clear/terminal (`RunState()` / inactive), leave `startedAtEpochMs = null`.

- [ ] **Step 3: Grep all `RunState(` / `status = RunStatus.` constructions; ensure no compile breaks**

```bash
rg -n 'RunState\(|startedAtEpochMs' app/src -g'*.kt'
```

- [ ] **Step 4: Compile**

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:compileDebugKotlin --no-daemon --console=plain
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/deerflow/mobile/data/Models.kt \
  app/src/main/java/com/deerflow/mobile/run/RunCoordinator.kt
# plus any cache codec touch
git commit -m "feat: record startedAtEpochMs on active RunState"
```

---

### Task 3: Strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`

- [ ] **Step 1: Add strings** (reuse `run_running` / `thinking` where possible; add only what’s missing)

```xml
<!-- values/strings.xml -->
<string name="run_activity_working">Working…</string>
<string name="run_duration_less_than_second">&lt;1s</string>
<string name="run_duration_hours">%1$dh</string>
<string name="run_duration_minutes">%1$dm</string>
<string name="run_duration_seconds">%1$ds</string>
<string name="run_activity_elapsed">(%1$s)</string>
```

```xml
<!-- values-zh-rCN/strings.xml -->
<string name="run_activity_working">正在执行…</string>
<string name="run_duration_less_than_second">&lt;1秒</string>
<string name="run_duration_hours">%1$d小时</string>
<string name="run_duration_minutes">%1$d分</string>
<string name="run_duration_seconds">%1$d秒</string>
<string name="run_activity_elapsed">（%1$s）</string>
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml
git commit -m "feat: add run activity string resources"
```

---

### Task 4: RunActivityRow composable

**Covers:** Web `RunActivity` visual parity (clock/loader + working + elapsed)

**Files:**
- Create: `app/src/main/java/com/deerflow/mobile/ui/RunActivityRow.kt`

- [ ] **Step 1: Implement**

```kotlin
// app/src/main/java/com/deerflow/mobile/ui/RunActivityRow.kt
package com.deerflow.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deerflow.mobile.R
import kotlinx.coroutines.delay

internal object UiTagsRunActivity {
    const val RunActivity = "run-activity"
}

@Composable
fun RunActivityRow(
    startedAtEpochMs: Long?,
    modifier: Modifier = Modifier,
) {
    var elapsedSeconds by remember(startedAtEpochMs) { mutableIntStateOf(0) }
    LaunchedEffect(startedAtEpochMs) {
        if (startedAtEpochMs == null) {
            elapsedSeconds = 0
            return@LaunchedEffect
        }
        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - startedAtEpochMs) / 1000L)
                .toInt()
                .coerceAtLeast(0)
            delay(1000)
        }
    }
    val labels = RunDurationLabels(
        lessThanSecond = stringResource(R.string.run_duration_less_than_second),
        hours = { stringResource(R.string.run_duration_hours, it) },
        minutes = { stringResource(R.string.run_duration_minutes, it) },
        seconds = { stringResource(R.string.run_duration_seconds, it) },
        separator = " ",
    )
    val formatted = formatRunDuration(elapsedSeconds, labels)
    Row(
        modifier = modifier
            .testTag(UiTagsRunActivity.RunActivity)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LoadingIndicator(Modifier = Modifier.size(18.dp))
        Icon(
            Icons.Outlined.Schedule,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.run_activity_working),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (formatted != null) {
            Text(
                stringResource(R.string.run_activity_elapsed, formatted),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

Note: If both `LoadingIndicator` + clock feels busy, drop clock icon and keep loader only (still matches “working” intent). Prefer **loader + working text + elapsed** (Web uses clock + shimmer; Material loader is the Android equivalent of activity).

- [ ] **Step 2: Compile**

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:compileDebugKotlin --no-daemon --console=plain
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/deerflow/mobile/ui/RunActivityRow.kt
git commit -m "feat: add RunActivityRow composable"
```

---

### Task 5: Wire into ChatScreen

**Covers:** visible while conversation run is active

**Files:**
- Modify: `app/src/main/java/com/deerflow/mobile/ui/ChatScreen.kt`

- [ ] **Step 1: Placement**

Inside conversation content (after `ConversationMessageList` box or as last LazyColumn item), when:

```kotlin
val showRunActivity = state.run.active && state.selectedThread != null
```

Show:

```kotlin
if (showRunActivity) {
    RunActivityRow(startedAtEpochMs = state.run.startedAtEpochMs)
}
```

**Preferred placement:** bottom of the message list area (above composer), not covering input — e.g. below the `Box(weight(1f))` list or as sticky footer inside that column.

Do **not** remove `ThinkingIndicator` / tool `LoadingIndicator`; this is **additional** run-level chrome.

- [ ] **Step 2: Manual check on emulator**

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleDebug --no-daemon --console=plain
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Send a short message; expect Working… + ticking elapsed; on EndEvent row disappears
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/deerflow/mobile/ui/ChatScreen.kt
git commit -m "feat: show run activity row during active conversation runs"
```

---

### Task 6 (optional follow-up): Completed duration

**Covers:** Web `RunDuration` after last group

**Only if** assistant messages already carry `turn_duration` in `additional_kwargs` / token metadata already parsed:

- [ ] Grep:

```bash
rg -n 'turn_duration|turnDuration' app/src -g'*.kt'
```

- [ ] If present: pure function `completedRunDurations(messages)` + small label under last assistant group.
- [ ] If **not** present: **skip** — do not parse new gateway fields in this plan.

---

### Task 7: Regression suite

- [ ] Run:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew testDebugUnitTest lintDebug --no-daemon --console=plain
```

Expected: BUILD SUCCESSFUL

- [ ] Confirm no crash open of image threads still works (prior markdown image work).

---

## Self-review

1. **Coverage:** Active working + elapsed (Tasks 1–5); completed duration only if data exists (Task 6). Tool/thinking loaders unchanged.
2. **No placeholders:** Concrete files, tests, commands.
3. **Types:** `startedAtEpochMs: Long?` on `RunState`; `formatRunDuration(Int, RunDurationLabels)`.

## Success criteria

- During active run on open thread: row shows working label + updating elapsed each second.
- When run ends: row gone; existing Thinking/tool indicators still work.
- Unit tests for duration formatting pass; full unit tests + lint pass.
