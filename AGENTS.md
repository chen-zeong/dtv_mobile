# Repository Guidelines

## Project Structure & Module Organization

This is an Android-only Kotlin Multiplatform project using Compose Multiplatform. `androidApp/` contains the application entry point and Android resources; `shared/src/commonMain/` contains shared models, state, networking, and UI; `shared/src/androidMain/` contains Android-specific `actual` implementations, platform APIs, and assets. Screenshots and documentation live at the repository root.

Keep UI in `ui/`, state in `state/`, repository contracts in `repo/`, and platform clients under `platform/<name>/`. Add platform-specific code to `androidMain`, using `expect`/`actual` only when a shared API is required.

## Build, Test, and Development Commands

Run commands from the repository root:

```powershell
.\gradlew.bat :androidApp:assembleDebug   # Build a debug APK
.\gradlew.bat :androidApp:installDebug     # Install on a connected device
.\gradlew.bat :androidApp:lintDebug        # Run Android lint
.\gradlew.bat :androidApp:testDebugUnitTest # Run unit tests, if present
```

Requires JDK 17, Android SDK 36, and an Android device or emulator. There are currently no test source sets or formatting plugins configured; add focused tests under `shared/src/commonTest/` or `androidApp/src/test/` when introducing testable behavior.

## Coding Style & Naming Conventions

Follow `kotlin.code.style=official`, the repository's two-space indentation, and trailing commas in multi-line declarations. Use PascalCase for classes, composables, and types; camelCase for functions, properties, and parameters; and `UPPER_SNAKE_CASE` for constants. Keep `@Composable` functions focused, and prefer existing Material 3 and project components over new UI primitives.

## Testing Guidelines

Use JUnit-compatible tests for Kotlin logic and Compose UI tests for user flows. Name tests after observable behavior, such as `loadsFollowedStreamersWhenOnline`. No coverage threshold is configured; add regression tests for bug fixes and run the narrowest relevant Gradle test task before committing.

## Commit & Pull Request Guidelines

The supplied GitHub repository could not be inspected from this environment, so no historical convention is asserted. Use short, imperative commit subjects such as `Fix player rotation state`. Pull requests should explain the change, list verification commands, link related issues, and include before/after screenshots for visible UI changes.

## Configuration & Security

Do not commit `androidApp/keystore.properties`, signing keys, cookies, tokens, or captured logs. Copy `androidApp/keystore.properties.example` locally for release builds and keep credentials outside source control.
