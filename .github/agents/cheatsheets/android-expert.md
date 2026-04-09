# Android Expert — Personal Cheatsheet

> Read at the start of every run. Update after every run with new patterns, gotchas, and fixes.

## Build Commands
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:compileDebugKotlin --no-daemon -q   # fast compile check
./gradlew :app:assembleDebug --no-daemon            # full APK
```

## FinancialCatalog — Key Patterns
- ALL widget UI changes go in `app/src/main/java/com/example/a2ui/chat/data/a2ui/FinancialCatalog.kt`
- Widget val declarations MUST come before `val FinancialCatalog` (Kotlin top-level init order)
- Rightmost catalog in `CoreCatalog + Catalog.of(...)` wins — chart widgets registered first
- `DataReferenceParser.parseString(data["key"])` → `LiteralString` or `PathString`

## Canvas/Compose Imports (common pitfall)
- `Modifier.size()` needs explicit import: `androidx.compose.foundation.layout.size`
- `Modifier.fillMaxSize()` needs: `androidx.compose.foundation.layout.fillMaxSize`
- `DrawScope.size` (inside Canvas lambda) works without import

## CompositionLocals (Row ↔ Text signaling)
- `LocalAccentColorSink` — `(Color) -> Unit` written by amount Text via SideEffect
- `LocalBodyEmphasis` — Boolean upgrades body-hinted Text to SemiBold in transaction rows
- Outer Row uses `IntrinsicSize.Min` so fillMaxHeight() works

## TextField Data Binding
- Must include explicit `text: { path: "..." }` binding matching button's actions context path
- Client reads `text.path` for both initial value read AND keystroke write
- Values seeded via `LaunchedEffect` on composition

## Button Style Detection
- Server sends `style: "filled"` / `style: "outlined"` (not `primary: true/false`)
- Override checks `style` first, falls back to `primary` bool

## Column Spacing Tokens
- `"form"` → 16dp, `"fieldGroup"` → 4dp, omitted → 2dp

## Logcat Tags
| Tag | Source |
|---|---|
| `A2UI.VM` | ChatViewModel |
| `A2UI.Repo` | RealChatRepository |
| `A2UI.Surface` | SurfaceStateManager |
| `FinancialCatalog` | Widget overrides |

## Networking
- Emulator reaches host at `10.0.2.2:8000` (hardcoded in RealChatRepository)
- Physical devices need host LAN IP

## Known Gotchas
- `set -u` in bash + Unicode emoji in `case` statement → `unbound variable` error; use `if/elif` instead
- Chart widgets use embedded `JsonArray` in componentProperties, NOT DataContext path bindings

## Session Log
| Date | Pattern Learned |
|---|---|
| 2026-04-09 | DonutChart/BarChart: Canvas drawArc for ring segments, IntrinsicSize.Min for accent bar |
