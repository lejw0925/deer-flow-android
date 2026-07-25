# Repository Guidelines

## Project Structure & Architecture

This is a single-module Kotlin Android client. Production code is under
`app/src/main/java/com/deerflow/mobile/`: `data/` contains Gateway, Room, and
DataStore code; `run/` owns active-run coordination and notifications; and
`ui/` contains Compose screens, presentation helpers, and themes. Android
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
