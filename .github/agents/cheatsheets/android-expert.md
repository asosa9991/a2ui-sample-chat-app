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
- `LocalListItemPath` — `String?` per-item DataContext path prefix; set by financialListWidget for each item so ListItem widget can resolve relative field paths
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

## Serialization Gotchas
- **ComponentDto id-fallback**: sync `/chat/template` endpoint omits `id` from component objects — map key IS the id. Fix: `val id: String? = null` + `dto.id ?: key` in `toDomain()`
- **A2UI DataModel.getString() throws on JsonObject**: calling `getString()` on a path that resolves to a JsonObject throws `IllegalArgumentException` (does NOT return null). Null only means missing/out-of-bounds. Always wrap array-probe calls in try-catch.

```kotlin
// Correct array-probe pattern for DataContext with object-valued items
val itemExists = try {
    dataContext.getString("$path/$index") != null
} catch (_: IllegalArgumentException) {
    true  // exists but is a JsonObject — not a primitive
}
```

## Session Log
| Date | Pattern Learned |
|---|---|
| 2026-04-09 | DonutChart/BarChart: Canvas drawArc for ring segments, IntrinsicSize.Min for accent bar |
| 2026-05-30 | ListItem widget: CompositionLocalProvider per-item path scoping; semantics{invisibleToUser()} on child Texts + contentDescription on root Row for TalkBack; HorizontalDivider between items using DividerColor from theme |
| 2026-06-02 | BackendMode toggle: add `endpoint: String` param with default to interface + impl; StateFlow toggle in VM; stateless segmented pill composable with animateColorAsState + press scale |
| 2026-06-02 | Idempotent mode selection: use `_state.value = mode` setter instead of toggle; add `.selectableGroup()` to Row wrapping RadioButton semantics items for TalkBack radio-group announcement |
| 2026-06-03 | WireFormat toggle + 4-endpoint routing matrix: when adding endpoint param to private fun, remove the internal endpoint computation block too — compiler error "too many arguments" if both exist; also update interface default impl to pass endpoint through |
| 2026-04-10 | Unit test Android.util.Log not mocked: add `unitTests.isReturnDefaultValues = true` in testOptions block in build.gradle.kts — fixes "Method i in android.util.Log not mocked" errors across all unit tests |
| 2026-06-03 | SYNC error UX: flow catch blocks must emit user-friendly strings (not raw e.message); ViewModel must pass event.error directly as content (no "Error: " prefix) — keep parity with non-streaming sendMessage() error behavior |
| 2026-06-04 | ComponentDto id nullable fallback: sync endpoint omits id from objects; A2UI getString() throws IllegalArgumentException on JsonObject paths (not null) — use try-catch probe pattern for all array-item existence checks |
| 2026-06-05 | Bug fixes: (1) sync agent.py must wrap entry["component"] as {"componentProperties": entry["component"]} to match ComponentDto; (2) financialRowWidget/financialColumnWidget need jsonArray import + explicitList fallback after parseComponentArray; (3) financialCardWidget plain-string child needs `(data["child"] as? JsonPrimitive)?.contentOrNull` fallback; `jsonArray` extension property requires explicit `import kotlinx.serialization.json.jsonArray` |
| 2026-06-05 | Kotlin regex replacement `$` crash: `String.replace(Regex, String)` delegates to Java `Matcher.replaceAll(String)` — bare `$` in replacement is a Java group-reference escape → `IllegalArgumentException`. Always write `"positive \\$"` (Kotlin source) to emit literal `$` in output. |
