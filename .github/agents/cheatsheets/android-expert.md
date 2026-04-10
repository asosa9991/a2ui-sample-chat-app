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
- **Regex replacement `$` is fatal**: `String.replace(Regex, "text $")` → Java `Matcher.replaceAll()` treats `$` as group reference → `IllegalArgumentException`. Always write `"text \\$"` (Kotlin source) for literal `$` in replacement. Rule: any new string transform with `$` in replacement MUST have a unit test.
- **monetaryColor() drives bar AND text color**: the left accent bar and value text both read from `monetaryColor(value)`. If you add new color logic here, add to `MonetaryColorTest.kt`.
- **explicitList vs componentIds**: templates use `{"explicitList": [...]}` for children; A2UI standard is `{"componentIds": [...]}`. Always add the fallback: `?: (childrenEl as? JsonObject)?.get("explicitList")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()` after `parseComponentArray()`.
- **Card child plain-string**: templates pass `"child": "comp_id"` (plain string). `parseComponentRef()` expects an object. Always add `?: (data["child"] as? JsonPrimitive)?.contentOrNull` fallback.

## Serialization Gotchas
- **ComponentDto id-fallback**: sync `/chat/template` endpoint omits `id` from component objects — map key IS the id. Fix: `val id: String? = null` + `dto.id ?: key` in `toDomain()`
- **A2UI DataModel.getString() throws on JsonObject**: calling `getString()` on a path that resolves to a JsonObject throws `IllegalArgumentException` (does NOT return null). Null only means missing/out-of-bounds.

- **List probe — correct approach**: Use `getObjectKeys()` + `getString()` to detect item existence. `getObjectKeys()` uses `as? JsonObject` (no throw), returns non-null for object items; `getString()` covers primitive items. Combined: `getObjectKeys("$path/$index") != null || getString("$path/$index") != null`. The old try-catch (`getString()` + catch `IllegalArgumentException`) was correct but fragile — `getObjectKeys()` is the clean, throw-free replacement.
- **DataContext interface has NO `get()` method** — only `getString`, `getNumber`, `getBoolean`, `getStringList`, `getArraySize`, `getObjectKeys`, `update`, `withBasePath`. Use `getObjectKeys()` when you need non-null detection for JsonObject-valued paths. Use `getArraySize()` for JsonArray-valued paths.
- **List probe — complete three-branch pattern**: `getObjectKeys("$path/$index") != null || getArraySize("$path/$index") != null || getString("$path/$index") != null`. All three branches are non-throwing. `getString()` MUST come last because it throws `IllegalArgumentException` for both JsonObject AND JsonArray paths — guard it with the two prior branches.
- `buildJsonObject { put("key", "string") }` in unit tests: MUST add `import kotlinx.serialization.json.put` — without it, Kotlin can't find the `put(String, String)` overload and falls back to `put(String, JsonElement)` causing a compile error
- `Map<String, JsonObject>` is NOT assignable to `Map<String, JsonElement>` without use-site variance; use `mapOf<String, JsonElement>("key" to obj)` or declare the variable as `Map<String, JsonElement>`
- `Color` from `androidx.compose.ui.graphics.Color` is a `@JvmInline value class` — avoid using it in JVM unit tests; mirror logic with enums/strings instead
- Mirror approach for private functions: define a local helper that replicates the logic (returns enum/string instead of Color), test the branching — same pattern as ExplicitListParsingTest and ValueSemanticTest
- `SurfaceStateManager`: can be instantiated directly in unit tests; `buildUiDefinition()` returns null if `surfaceId==null` OR `components.isEmpty()` — always feed a `surfaceUpdate` op first to seed both

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
| 2026-06-06 | New unit tests: MonetaryColorTest (11 tests), DataContextPathResolutionTest (10 tests), SurfaceStateManagerTest (17 tests), ListItemRenderTest (6 instrumented); `import kotlinx.serialization.json.put` required for `put(String, String)` in buildJsonObject; mirror private functions with enum return types for JVM unit tests |
| 2026-06-07 | Sync dataModel: UiDefinitionDto needs `dataModel: List<DataModelEntryDto>?` + `buildDataModelJson()` extension; RealChatRepository.sendMessageSyncAsFlow must pass `dataModelJson = agentResponse.uiDefinition?.buildDataModelJson()`; Divider widget must be registered in FinancialCatalog as `financialDividerWidget` using HorizontalDivider — HorizontalDivider already imported at line 73 |
| 2026-06-09 | List probe fix: replace try-catch probe with `getObjectKeys() != null \|\| getString() != null`; `DataContext` has no `get()` method — `getObjectKeys()` is the throw-free equivalent for JsonObject-valued paths |
| 2026-06-09 | List probe JsonArray fix: `getString()` also throws for JsonArray paths — added middle guard `getArraySize("$path/$index") != null`; three-branch probe is now fully non-throwing for all JSON value types |
| 2026-06-09 | Sync dataModelJson bug: `sendMessageSync` `StreamEvent.Done` branch was hard-coding `dataModelJson = null` — fix is `event.message.dataModelJson`; A2UI DataContext always empty in Sync mode without this fix |

## Test DoD

Before marking any FinancialCatalog change done, verify:
- [ ] Widget renders without crash (Compose UI test or `RenderingRegressionTest`)
- [ ] Any new string transform: unit test with `$`-prefixed, `-$`, `+$` inputs
- [ ] Any new children-parsing: unit test with `explicitList` AND `componentIds` formats
- [ ] Any new color logic: unit test in `MonetaryColorTest`
- [ ] Run `./gradlew :app:testDebugUnitTest` — must stay green
