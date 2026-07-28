# MI-84 Calculator Mod — Project Context

## Stack

- **Fabric mod** for Minecraft, Kotlin source
- `build.gradle.kts`: Fabric Loom remap, Kotlin 2.3.21, JVM 21
- Split source sets: `main` (server/common) + `client`
- Dependencies: fabric-loader, fabric-api, fabric-language-kotlin
- Official Mojang mappings (not Yarn)

## Source files

| File | Purpose |
|---|---|
| `src/main/kotlin/.../Mi84_calc.kt` | Server-side `ModInitializer` stub |
| `src/client/kotlin/.../Mi84_calcClient.kt` | Hooks `InventoryScreen`, adds the `X` toggle and calculator overlay |
| `src/client/kotlin/.../calculator/CalculatorWidget.kt` | Thin Minecraft widget adapter: texture, dragging, hit-testing, and input forwarding |
| `src/client/kotlin/.../calculator/CalculatorRenderer.kt` | Shared LCD rendering and non-graph calculator views |
| `src/client/kotlin/.../calculator/CalculatorGraphRenderer.kt` | Axes, functions, trace, and interactive zoom rendering |
| `src/client/kotlin/.../calculator/input/CalculatorKey.kt` | Stable typed identities for all 50 physical keys |
| `src/client/kotlin/.../calculator/input/CalculatorKeyLayout.kt` | Texture-relative physical hitboxes with no behavior |
| `src/client/kotlin/.../calculator/input/CalculatorCommand.kt` | Typed Normal, Phase 1/2 and approved Phase 4 2nd commands, Alpha variables, and explicit remaining placeholders |
| `src/client/kotlin/.../calculator/controller/CalculatorController.kt` | Central input dispatch, view transitions, graph, trace, and zoom behavior |
| `src/client/kotlin/.../calculator/controller/CalculatorViewControllers.kt` | Home, Y=, Window, and Mode input behavior |
| `src/client/kotlin/.../calculator/ui/CalculatorUiState.kt` | Active view, modifier/A-LOCK, history/ENTRY, insert mode, compact/function menus, fraction template, trace, and interactive zoom state |
| `src/client/kotlin/.../calculator/ui/CompactMenuState.kt` | Reusable non-Minecraft compact-menu definitions, tabs, items, availability, selection, and return target |
| `src/client/kotlin/.../calculator/ui/FunctionMenuState.kt` | Bottom-tab F1–F4 overlay definitions, editor targets, typed actions, and transient structured fraction fields |
| `src/client/kotlin/.../calculator/ExpressionEditingTokens.kt` | Shared atomic cursor recognition for completed structured fractions |
| `src/client/kotlin/.../calculator/MathDisplayTokens.kt` | Pure recognition of complete/in-progress fraction, radical, and combinatoric display tokens |
| `src/client/kotlin/.../calculator/CalculatorPosition.kt` | In-memory calculator position shared between inventory sessions |
| `src/client/kotlin/.../calculator/CalculatorDisplayMemory.kt` | Persistent input/history store and real scalar expression evaluator |
| `src/client/kotlin/.../calculator/CalculatorVariableMemory.kt` | Persistent real/rectangular-complex scalar A-Z/θ storage with legacy X import |
| `src/client/kotlin/.../calculator/ComplexExpressionEvaluator.kt` | Principal-value complex arithmetic/parser fallback for rectangular complex mode |
| `src/client/kotlin/.../calculator/CalculatorPersistence.kt` | Logged temp-file persistence with atomic replacement where supported |
| `src/client/kotlin/.../calculator/GraphNavigationMath.kt` | Testable trace clamping and large integer-bound helpers |
| `src/client/kotlin/.../calculator/ModeSettingsMemory.kt` | Persistent Mode categories/options plus numeric-display and angle/complex mode accessors |
| `src/client/kotlin/.../calculator/YEqualsMemory.kt` | Persistent Y1–Y9 graph expressions, colors, and edit cursors |
| `src/client/kotlin/.../calculator/WindowSettingsMemory.kt` | Persistent graph-window settings and edit cursors |
| `src/client/kotlin/.../calculator/ZoomMemory.kt` | Persistent zoom memory and selected fractional-zoom factor |
| `src/client/kotlin/.../Mi84_calcDataGenerator.kt` | Empty `DataGeneratorEntrypoint` stub |
| `src/client/resources/assets/mi84_calc/textures/calculator/calc.png` | Calculator artwork (440×1024 source texture) |
| `src/client/resources/assets/mi84_calc/textures/calculator/calc.png.mcmeta` | Nearest-neighbor texture settings to keep the scaled pixel art sharp |
| `src/main/java/.../mixin/ExampleMixin.java` | Unused mixin template (HEAD inject on `MinecraftServer.loadLevel`) |
| `ARCHITECTURE.md` | Component ownership, dependency rules, extension workflow, and verification strategy |
| `BUTTON_MATRIX.md` | Authoritative Normal, 2nd, and Alpha behavior for every physical key |

## Architecture

- **Client-only mod** — no server-side calculator logic is needed.
- `Mi84_calcClient.onInitializeClient()` registers `ScreenEvents.AFTER_INIT`.
  - When an `InventoryScreen` opens, it adds a bottom-left `X` button and a hidden `CalculatorWidget` through `Screens.getButtons(screen)`.
  - The button toggles the calculator widget; it does not open a separate screen.
  - Three configurable client keybindings appear under the **MI-84 Calculator** Controls category and only work while an inventory is open: **Reset calculator position** (default `N`) immediately repositions the overlay and clears its remembered position; **Toggle calculator visibility** (default `H`) matches the `X` button's overlay toggle; and **Toggle calculator button visibility** (default `K`) shows or hides that bottom-left `X` button.
- `CalculatorWidget` extends `AbstractWidget` and deliberately remains a thin Minecraft adapter.
  - It renders `calc.png` at a 110×256 default size, scaling the 440×1024 source texture by 0.25.
  - It forces non-blurred, non-mipmapped texture filtering before rendering.
  - It defaults to the left of the inventory using placeholder inventory dimensions (176 px) and a 12 px gap.
  - Dragging is enabled only from the 44 px top strip.
  - `CalculatorKeyLayout` owns texture-relative hitboxes. Mouse clicks become a typed `CalculatorInputEvent` and enter through `CalculatorController.dispatch`; the calculator does not accept computer keyboard input.
  - Implemented primary keys include digits, arithmetic, decimal, sign, powers/reciprocal, parentheses, `sin`/`cos`/`tan`/`log`/`ln`, `X`, scalar `sto->variable`, comma, Clear, Enter, Del, cursor arrows, history navigation, Y=, Window, Zoom, Mode, Trace, and Graph.
  - 2nd and Alpha are normally one-shot modifier layers. `2nd` then Alpha enables persistent-session A-LOCK with a gray-header indicator; Alpha cancels it, and a temporary 2nd command returns to the lock. `2nd` + Mode is Quit and returns to Home. Phase 1 scalar 2nd mappings implement explicit `Ans`, `i`, `π`, `e`, inverse trig, `10^(`, `e^(`, `sqrt(`, and `EE`. Phase 2 adds ENTRY, INS, and typed Alpha A-Z/θ variables; every unresolved shifted meaning remains an explicit placeholder and cannot fall through to the primary action. Zoom retains its Alpha A-G shortcuts.
  - The reviewed Phase 4 compact TEST menu exposes TEST relations and LOGIC tokens through tabs, arrows, numeric hotkeys, Enter, Clear, and direct-view exits. CONDITIONS rows are visible but unavailable pending editable-template review. `and`, `or`, and `xor` move, overwrite, and forward-delete as whole editor tokens. Available tokens return to the originating Home, Y=, or Window editor; other origins return to Home.
  - The reviewed ANGLE menu shows all eight rows without scrolling. Degree/radian markers and `R►Pr(`, `R►Pθ(`, `P►Rx(`, and `P►Ry(` are implemented with active Angle Unit semantics. Minute notation and `►DMS` are visible but unavailable.
  - The reviewed MATH menu exposes five MATH/NUM/CMPLX/PROB/FRAC tabs through the shared nine-row compact viewport. Numeric and displayed Alpha A-D hotkeys, arrows, Enter, Clear, and direct-view exits use the same typed routing. Cube, cube root, nth root, `logBASE`, the Phase 3 NUM helpers, `abs`, `nPr`, `nCr`, factorial, and the shared fraction templates are available. Conversion, numerical-calculus, piecewise/solver, broader complex, and random-generator rows remain visibly unavailable.
  - Square, cube, and nth roots render slightly larger, matching the structured fraction's visual weight, with a radical, a small gap before the radicand, and an overbar sized to the displayed content; cube/nth indices are compact at the radical. Only an empty indexed-root index shows a dotted placeholder; the radicand has no dotted box. Right advances index-to-radicand, then exits. New menu entry stores indexed roots as `root(index,value)` so field order matches the display, while legacy `nthRoot(value,index)` remains evaluable. `nPr` and `nCr` render at the same enlarged visual weight with compact lowered operands around `P` or `C`; only empty operands show dotted placeholders. Their cursor is compact/lowered inside an operand, and Right advances left-to-right and then exits without hidden delimiter stops. Complete and in-progress expressions hide their generic evaluator names.
  - Alpha Y= and Alpha Window open the reviewed F1 FRAC and F2 FUNC bottom-tab overlay. It retains Home, Y=, or Window behind the boxed options and inserts into that view; non-editable origins return to Home. Left/right switches among visible FRAC/FUNC/MTRX/YVAR tabs, while F3/F4 remain unavailable. `2nd` + Mode closes only this overlay and retains its underlying editor.
  - FRAC supplies compact inline fraction and mixed-number fields at the active cursor. Numerator/denominator text is smaller so the stacked fraction is about 50% taller than an ordinary number. Its bar uses a half-logical-pixel vertical transform, is separated from cursor highlighting, and is sized to the wider field. Completed templates emit internal `frac`/`mixed` expressions, but the renderer keeps a visible stacked bar for entry/history and reduced results. Left immediately after a completed fraction reopens denominator-first editing, then moves to numerator and mixed whole-number fields; within a field, only the character under the blinking forward cursor is inverted and edited. Moving Left past the first field commits and exits to a narrow cursor before the fraction, restoring the original token if an edited field is incomplete. Right from that leading position reopens the first field and traverses the fraction left-to-right. The cursor uses unscaled glyph positions before the fraction transform so multi-digit fields stay centered. Outside that edit session the structured fraction remains one overwrite/delete token across Home, Y=, and Window. Recalling a fraction result reconstructs that structured token instead of ordinary `/` division. Home results reduce to a fraction unless the expression also contains a decimal point. The two conversion rows remain unavailable.
  - FUNC exposes `abs`, `logBASE`, square/indexed roots, permutations, combinations, and postfix factorial. Numerical derivative, numerical integral, and summation remain visible but unavailable.
  - It renders LCD text at half the built-in Minecraft font size within the source-texture bounds `(21, 87)` to `(418, 343)`: active input is left-aligned, completed input remains left-aligned, and its result is right-aligned below a divider. Up/down selects history rows; Enter recalls a selected non-error row into the current expression. `2nd` + Enter (ENTRY) instead replaces the edit line with submitted inputs newest-first.
  - The gray LCD header strip from source-texture `(21, 49)` to `(418, 84)` always shows the active Number Display, Decimal Display, Answers, Complex Number Format, and Angle Unit values as one row of white text on every LCD view.
  - Its LCD has direct, in-overlay `HOME`, `Y_EQUALS`, `WINDOW`, `ZOOM`, `MODE`, and `GRAPH` views. The Y=, Window, Zoom, Mode, and Graph keys switch directly between their views; `2nd` + Mode (Quit) returns to Home from any calculator view.
  - The Y= view shows all nine color-coded `Y₁`–`Y₉` expressions at once. Up/down chooses an expression, left/right moves its forward-edit cursor, and the calculator's entry keys edit it.
  - The Window view shows `Xmin`, `Xmax`, `Xscl`, `Ymin`, `Ymax`, `Yscl`, `Xres`, `ΔX`, and `TraceStep` at once. Up/down chooses a setting and the calculator's entry keys edit it.
  - The Mode view contains all persistent setting categories in a ten-row scrolling viewport. Up/down changes category and left/right changes its selected option immediately. Every category's selected option is rendered as white text in a black box; the active option blinks. The Mode view uses an extra 10 source-texture pixels at the bottom of the LCD.
  - The Graph view plots every non-empty Y= expression in its assigned color. It uses X/Y bounds and scales for the axes and tick marks, plus Xres for horizontal sampling. Invalid graph bounds/settings display `INVALID WINDOW`.
  - Trace opens the Graph view at Y1 and the midpoint of the X window. Its blinking pixel `X` stays centered over the evaluated point; left/right moves it by `TraceStep`, and up/down skips between non-empty Y= expressions. Trace reserves an LCD header/footer for the selected colored equation and its colored `X=`/`Y=` readout.
  - The Zoom view has scrolling `ZOOM` and `MEMORY` tabs, navigable with arrows/Enter and menu hotkeys. It supports ZBox, cursor-centered Zoom In/Out using the selected factor, ZDecimal, ZSquare, ZStandard, ZTrig, ZInteger, ZoomStat/ZoomFit, ZQuadrant1, and fractional grid presets from `1/2` through `1/10`. ZBox/Zoom In/Out open the Graph view with a movable cursor; Enter selects the box anchor or applies the centered zoom.
  - As an inventory-owned widget, it disappears when the inventory closes. E and Esc retain normal inventory behavior.
- `CalculatorPosition` retains the dragged coordinates in memory between inventory open/close sessions. It does not yet persist across game restarts.
- `CalculatorDisplayMemory` saves the active expression and all submitted input/result pairs in `config/mi84_calc_display_memory.txt`.
  - At most 1,000 submitted entries are retained. Adding or loading beyond the cap removes the oldest entries first.
  - When LCD space is exhausted, old rows are hidden without immediately deleting retained history.
  - Pressing Clear with active input clears that input; pressing Clear on an empty input hides the visible LCD history without deleting saved entries.
  - The expression editor has a forward-delete cursor. Entry normally replaces the token beneath the cursor; `2nd` + Del toggles a transient, indicator-free insert mode shared by Home, Y=, and Window. Recalled up/down history is still inserted at the cursor.
  - Recalling a history row that would exceed the 31-character input limit currently fails silently and exits history navigation.
  - `Ans` resolves to the most recent valid real or rectangular-complex result and can be inserted explicitly with `2nd` + `(−)`. In an empty input, `+`, `-`, `*`, `/`, and `^` begin an `Ans` expression for the next user operand; square and reciprocal apply to `Ans`. Raw real and imaginary result components are persisted separately so display rounding does not reduce later `Ans` precision.
  - The real evaluator supports standard precedence, implicit multiplication (`8X`, `2(X+1)`, `3sin(X)`, `2π`, `2A`), automatic completion of omitted trailing function/template parentheses in both Home and graph/Y= evaluation, parentheses, unary minus, right-associative exponentiation, `π`, `e`, `EE` scientific notation, forward and inverse angle-mode-aware trig, `log`, `ln`, `10^(`, `e^(`, `sqrt(`, `cubeRoot(`, `root(index,value)`, exact `frac`/`mixed`, `logBASE`, legacy `nthRoot(value,index)`, `nPr`, `nCr`, factorial, and persistent scalar variables.
  - When the real evaluator cannot represent a result and Mode is set to `a+bi`, `ComplexExpressionEvaluator` retries the complete expression with principal-value complex arithmetic. It supports arithmetic, powers, implicit multiplication, `Ans`, `i`, constants, scalar variables, parentheses, forward/inverse scientific functions, and square root. Rectangular results simplify unit coefficients (`i`, `-i`) and near-zero floating-point residue; for example, `sqrt(-1)` displays `i`.
  - Phase 3 evaluator foundations add `=`, `≠`, `>`, `≥`, `<`, `≤`, numeric `and`/`or`/`xor`/`not(`, comma-separated arguments, and scalar `abs`, `round`, `iPart`, `fPart`, `int`, `min`, `max`, `gcd`, `lcm`, and `remainder`. Phase 4 exposes reviewed TEST/LOGIC, partial ANGLE, and partial MATH-family entry paths. DISTR, random generators, and dependent MATH rows remain hidden or visibly unavailable.
- `CalculatorVariableMemory` saves A-Z and `θ` in `config/mi84_calc_scalar_variables.txt`.
  - Every scalar variable defaults to real zero; `expression→variable` stores real values or rectangular-complex values in `a+bi` mode.
  - X remains the graph coordinate during graph evaluation. Other real scalar variables may be used in graph equations; complex scalar values make that graph sample undefined.
  - The real X component is mirrored to the existing `x` line in `mi84_calc_display_memory.txt`. When the scalar file has no X, that legacy line is imported without removing or rewriting the legacy format.
- `ModeSettingsMemory` saves all current Mode selections in `config/mi84_calc_mode_settings.txt`.
  - Number Display (`Normal`/`Sci`/`Eng`) and Decimal Display (`Float`/fixed 0–9) control result and trace formatting without changing stored calculation precision.
  - Angle Unit (`Degree`/`Radian`) controls real and complex trigonometric evaluation.
  - Complex Number Format `a+bi` enables rectangular complex fallback evaluation; `Real` retains real-only evaluation. The other stored Mode choices remain available for calculator systems that have not been implemented yet.
- `YEqualsMemory` saves the nine Y= expressions in `config/mi84_calc_y_equals_memory.txt`; colors, selections, and forward-edit cursors are retained during the client session.
- `WindowSettingsMemory` saves graph settings in `config/mi84_calc_window_settings.txt`; defaults are Xmin `-10`, Xmax `10`, Xscl `1`, Ymin `-10`, Ymax `10`, Yscl `1`, Xres `1`, ΔX `5/66`, and TraceStep `5/33`.
- `ZoomMemory` saves a ZoomSto window and the selected Zoom In/Out denominator in `config/mi84_calc_zoom_memory.txt`. Zoom Previous toggles between the current and preceding window, ZoomSto saves the current window, ZoomRcl restores it, and SetFactors chooses `1/2`, `1/3`, `1/4`, `1/5`, `1/8`, or `1/10` for cursor-centered Zoom In/Out.

## Implementation status

### Complete

- Texture-backed calculator widget
- Inventory-only visibility and toggle button
- Dragging from the calculator top strip
- In-memory position retention between inventory sessions
- Inventory-only `N` position reset, `H` calculator visibility, and `K` calculator-button visibility hotkeys
- Crisp scaled texture rendering
- Texture-relative hitboxes for all calculator artwork buttons
- Persistent calculator input and display history
- LCD input/result history layout with non-destructive overflow and Clear behavior
- Digit entry, decimal point, unary sign, parentheses, Clear, and Enter
- Arithmetic evaluation for `+`, `-`, `*`, `/`, `^`, `x^2`, and `x^-1`, including `Ans`
- Phase 1 scalar 2nd functions: explicit `Ans`, `i`, `π`, `e`, inverse trig, `10^(`, `e^(`, linear `sqrt(`, and `EE`
- Phase 2 ENTRY recall, indicator-free INS editing, persistent scalar A-Z/θ storage, and Alpha variable input
- Phase 3 nonvisual relations, numeric Boolean logic, multi-argument parsing, and core MATH/NUM scalar helpers
- Phase 4 A-LOCK, compact TEST/LOGIC, partial ANGLE, partial MATH/NUM/CMPLX/PROB/FRAC, and bottom-tab F1 FRAC/F2 FUNC overlays; dependent CONDITIONS, DMS, fraction-conversion, complex/random, and numerical-calculus rows are visibly unavailable
- `sin`, `cos`, `tan`, `log`, `ln`, scalar variables/storage, forward delete, cursor movement, history recall, implicit multiplication, and omitted function-closing-parenthesis completion
- In-LCD Y= editor for nine colored expressions
- Persistent Y= expressions across game restarts
- Persistent in-LCD Window editor
- Persistent in-LCD Mode editor with scrolling, four-way navigation, and inverted selected options
- Normal/scientific/engineering and floating/fixed decimal display modes
- Degree and radian trigonometric evaluation
- Rectangular `a+bi` complex fallback evaluation, persistent complex `Ans`, and unit-imaginary simplification
- In-LCD graphing with configurable bounds, axis ticks, resolution, and colored functions
- Graph trace with a blinking point marker, function navigation, TraceStep movement, and LCD readouts
- In-LCD Zoom/Memory menu with preset, box, cursor-centered, fitted, fractional, previous, store/recall, and factor-selection zoom functionality
- Typed physical-key layout and exhaustive primary-command mapping
- Mouse-only calculator input events and central non-Minecraft controller
- Explicit one-shot 2nd/Alpha modifier layers, persistent A-LOCK, and typed Phase 1/2 plus approved Phase 4 mappings/placeholders
- 2nd + Mode Quit transition back to Home
- Split widget, renderer, transient UI state, and per-view input controllers
- Automated layout, overlap, mapping, modifier, and transition checks
- Logged temp-file persistence that preserves the last good file on failed saves

### Remaining

- Replace the documented 2nd/Alpha placeholders and unsupported primary commands one feature at a time
- Add table features that use ΔX and TraceStep
- Add broader multi-argument function families only with their owning typed domains
- Implement the stored non-rectangular Mode behaviors: graph types/order, polar complex display, split-screen layouts, fraction/exact-answer display, and statistics settings
- Add a dedicated calculator font/character sprites if the built-in Minecraft font is no longer desired
- Persist calculator position across game restarts
- Handle screen resizing/configurable placement more fully

## Key Fabric/Minecraft APIs in use

- `ScreenEvents.AFTER_INIT` — hook into screen creation
- `Screens.getButtons(screen)` — add the inventory toggle and custom widget
- `AbstractWidget` — renderable/input-capable calculator overlay base class
- `GuiGraphics.blit()` — draw the calculator texture
- `GuiGraphics.pose()` — scale the texture to the placeholder widget dimensions
- `TextureManager.getTexture(...).setFilter(false, false)` — nearest-neighbor texture filtering
- `GuiGraphics.drawString()` and `GuiGraphics.fill()` — LCD text and history dividers
- `FabricLoader.getInstance().configDir` — persistent calculator memory-file location

## Conventions

- Kotlin object syntax for singletons (`Mi84_calcClient`, `CalculatorPosition`, `CalculatorDisplayMemory`, `ModInitializer`)
- `Component.literal()` for visible labels; `Component.empty()` for the non-narrated calculator widget
- Keep unknown layout values as named placeholder constants with comments
- Use `CalculatorKey` and `CalculatorCommand`; never route behavior with visible-label strings
- Keep physical geometry in `CalculatorKeyLayout` and behavior in controllers/domain objects
- Keep unsupported primary behavior and future 2nd/Alpha behavior explicit in the command table
- Route new persistent files through `CalculatorPersistence`
- Preserve existing persistence formats unless a migration and regression test are included

## Future maintenance requirements

### Before implementing a button

1. Update `BUTTON_MATRIX.md` with its Normal, 2nd, and Alpha meanings.
2. Decide its behavior in every affected view and whether it consumes the active modifier.
3. Reuse the existing physical `CalculatorKey`; do not use display text as an identifier.
4. Add a typed command or view override.
5. Put behavior in the relevant controller or model, never directly in `CalculatorWidget`.
6. Add mapping, transition, and domain regression tests.

### Change boundaries

- Keep `CalculatorWidget` limited to Minecraft lifecycle, texture rendering, dragging, hit-testing, and input forwarding.
- Controllers and input/state types must not import Minecraft GUI classes.
- Rendering reads state but does not route keys or own calculator behavior.
- Persistent memory objects own saved domain data; `CalculatorUiState` owns only transient UI state.
- Avoid combining structural refactors, persistence-format changes, and new calculator behavior in one change.
- Treat a file approaching roughly 700 lines as a prompt to check whether it has multiple reasons to change; line count alone is not a failure.

### Required verification

- Run `./gradlew check build` for every implementation change.
- Maintain tests proving every physical key exists exactly once, hitboxes do not overlap, and primary mappings are exhaustive.
- Add tests for modifier activation, cancellation, one-shot consumption, and no fallthrough.
- Add characterization tests before moving untested behavior.
- Manually smoke-test the inventory overlay after rendering, sizing, hitbox, or Minecraft integration changes.
- Keep `ARCHITECTURE.md`, `BUTTON_MATRIX.md`, and this file synchronized with implementation changes.
