# MI-84 Calculator Architecture

## Purpose

The calculator is a client-only Fabric overlay attached to the Minecraft inventory screen. Its
calculator logic is intentionally separated from Minecraft widget APIs so input behavior can be
tested without starting the game.

## Runtime flow

```text
Mouse click on calculator artwork
        |
        v
CalculatorInputEvent(CalculatorKey)
        |
        v
CalculatorController.dispatch
        |
        +-- active view override (for example physical Zoom Alpha shortcuts)
        +-- Normal / 2nd / Alpha command resolution
        +-- direct view transition
        +-- active view controller
        |
        v
CalculatorUiState and persistent memory objects
        |
        v
CalculatorRenderer
        |
        v
CalculatorWidget / Minecraft GuiGraphics
```

## Component ownership

### Minecraft adapter

- `CalculatorWidget` owns texture drawing, dragging, mouse hit-testing, and the Minecraft
  `AbstractWidget` lifecycle. It deliberately does not handle computer keyboard input.
- `Mi84_calcClient` owns inventory integration, overlay visibility, and client keybindings.
- No expression, menu, graph, or modifier behavior should be added directly to the widget.

### Physical input

- `input/CalculatorKey.kt` defines stable identities for all 50 physical keys.
- `input/CalculatorKeyLayout.kt` defines texture-relative hitboxes only.
- `input/CalculatorCommand.kt` maps physical keys and modifier layers to typed logical commands.
- Primary commands are exhaustive. Phase 1 scalar 2nd meanings, Phase 2 ENTRY/INS and Alpha A-Z/θ,
  plus the approved Phase 4 TEST, ANGLE, MATH-family, F1 FRAC, and F2 FUNC menus have explicit typed
  commands; remaining unimplemented 2nd and Alpha meanings are explicit placeholders.

Visible legends are not identifiers. Code should compare `CalculatorKey.SIN`, not `"sin"`, and
should never infer behavior from displayed text.

### Controller and UI state

- `controller/CalculatorController.kt` is the single input-dispatch entry point.
- `controller/CalculatorViewControllers.kt` contains Home, Y=, Window, and Mode input behavior.
- `controller/DispatchResult.kt` distinguishes handled input, unsupported primary keys, and planned
  modifier placeholders.
- `ui/CalculatorUiState.kt` owns transient state: active view, modifier layer, history/ENTRY
  selection, insert/overwrite mode, persistent-session A-LOCK, compact-menu selection, function-menu
  overlay and fraction-template editing, trace state, and interactive zoom state.
- `ui/CompactMenuState.kt` defines reusable non-Minecraft tab/item/menu models and approved compact
  menu contents. Unavailable items have no action and cannot activate.
- `ui/FunctionMenuState.kt` defines the separate bottom-tab F1–F4 overlay, editable origin target,
  typed F1/F2 actions, and transient structured fraction fields.
- `ExpressionEditingTokens.kt` recognizes completed `frac`/`mixed` storage forms as one cursor,
  overwrite, and forward-delete token across Home, Y=, and Window.
- `MathDisplayTokens.kt` recognizes complete and in-progress fraction, root, permutation, and
  combination evaluator tokens for nonlinear LCD presentation without changing stored expressions.

Persistent calculator content does not belong in `CalculatorUiState`.

### Rendering

- `CalculatorRenderer` reads controller state and persistent stores to draw non-graph LCD views.
- The renderer draws A-LOCK in the shared mode header and renders compact menus, bottom-tab function
  overlays, unavailable items, compact inline structured fractions, adaptive radicals, and
  subscripted permutation/combination notation without owning menu behavior. Empty indexed-root
  indices and empty combinatoric operands include dotted placeholders; controller routing advances
  those fields, and cursor geometry follows their reduced/lowered text.
- `CalculatorGraphRenderer` draws axes, functions, trace, and interactive zoom markers.
- `CalculatorTextRenderer` supplies the shared half-scale Minecraft text primitive.
- Rendering must not mutate calculator behavior or persistent data, except the established Home
  overflow operation that hides rows already outside the LCD.
- Graph coordinate conversion and drawing stay in the rendering layer. Graph navigation and window
  changes stay in the controller/math layer.

### Domain and persistence

- `CalculatorDisplayMemory` owns Home input, history, real evaluation, and result formatting.
- `CalculatorVariableMemory` owns persistent real/rectangular-complex A-Z/θ scalar values. All
  variables default to zero. Its X value mirrors the legacy `x` display-memory line for backward
  compatibility.
- `ComplexExpressionEvaluator` owns complex fallback evaluation.
- The real and complex evaluators share the Phase 1 expression vocabulary: explicit `Ans`, `i`,
  `π`, `e`, inverse trig, `10^(`, `e^(`, `sqrt(`, and `EE`. Complex inverse functions and square
  root use principal values, while Degree/Radian mode controls inverse-trig output.
- Phase 3 established evaluator-only relations, numeric Boolean logic, comma-separated function
  arguments, and approved MATH/NUM scalar helpers before any matching menu was exposed. Phase 4's
  approved MATH-family menu now exposes those helpers, cube/cube-root entry, shared fraction
  templates, `nPr`, `nCr`, and factorial while leaving dependent rows unavailable. DISTR and random
  generators remain deferred.
- Phase 4 exposes reviewed TEST/LOGIC and partial ANGLE entry paths. Boolean word operators are
  whole editor tokens. Postfix degree/radian markers and rectangular/polar coordinate conversions
  live in the evaluator; DMS parsing/output remains unavailable.
- Phase 4 F1/F2 use a distinct overlay that retains Home, Y=, or Window as an explicit insertion
  target and redirects non-editable origins to Home. FRAC emits `frac`/`mixed` evaluator tokens and
  prefers reduced exact results unless the expression contains a decimal point. FUNC exposes
  `abs`, `logBASE`, square/indexed roots, permutations, combinations, and postfix factorial while
  keeping numerical derivative, integral, and summation unavailable. Quit closes only this overlay, and rendering
  translates internal fraction tokens and fraction answers into a small stacked bar at the text
  cursor. History-result recall reconstructs the structured fraction token so its exact-answer
  preference is not lost. Left at the token's trailing edge reopens transient denominator-first
  editing while retaining the original token until the edited replacement is committed. Moving
  Left past the first numerator/whole-number element commits valid edits, or restores an incomplete
  original, and exits to the cursor position before the structured token. Template fields use
  character-level forward cursors rather than whole-field selection. Their cursor block uses the
  shared 500 ms blink cadence and is drawn in unscaled font space before the fraction transform to
  avoid per-character rounding drift. At the token's leading edge the ordinary cursor is rendered
  as a narrow block before the stacked symbol; Right reopens the first field and traverses the
  fraction left-to-right.
- New indexed-root entry uses internal `root(index,value)` order so Right follows the visual
  index-to-radicand sequence; persisted `nthRoot(value,index)` expressions remain supported.
  Incomplete indexed roots and `nPr`/`nCr` advance to their second field with Right and add their
  closing delimiter when Right leaves the completed second field.
- `YEqualsMemory`, `WindowSettingsMemory`, `ModeSettingsMemory`, and `ZoomMemory` own their respective
  persistent domains.
- `CalculatorPersistence` is the only general file-writing boundary. New stores must use it rather
  than writing files directly.
- Existing file formats must remain backward compatible unless a documented migration and regression
  test are added.

## Input resolution rules

For every input event, the controller resolves behavior in this order:

1. The physical `2nd` or `Alpha` modifier key.
2. Active-view physical overrides: compact-menu navigation/numeric hotkeys/direct-view exits and
   Zoom Alpha A-G shortcuts. Explicit one-shot modifiers still prevent normal-command fallthrough.
3. The command from the active Normal, 2nd, or Alpha layer.
4. Direct view transitions such as Mode, Window, Graph, Trace, and 2nd Mode Quit.
5. The active view controller.

2nd and Alpha are normally one-shot layers. Pressing the same modifier twice cancels it. `2nd` then
Alpha enables persistent A-LOCK; Alpha cancels it, and a temporary 2nd command returns to it. A
non-modifier key consumes a one-shot layer even when its mapping is only a placeholder. Placeholders
must not fall through to the primary command.

## Adding or changing a button

1. Update `BUTTON_MATRIX.md` first with behavior for Normal, 2nd, and Alpha.
2. Reuse an existing `CalculatorKey`; add a new key only if the texture gains a physical key.
3. Add or update the typed command in `CalculatorCommand.kt`.
4. Put behavior in the relevant view controller or domain object, not in `CalculatorWidget`.
5. Decide whether the modifier is consumed, preserved, or changes view.
6. Return an explicit `Unsupported` or `Placeholder` result for unfinished behavior.
7. Add mapping, state-transition, and domain regression tests.
8. Run `./gradlew check build`.
9. Perform the manual overlay checks listed below.

## Verification strategy

Automated tests should cover:

- every physical key appearing exactly once;
- hitboxes staying in bounds and never overlapping;
- exhaustive primary mappings;
- explicit 2nd and Alpha placeholders;
- typed Phase 1/2 mappings, ENTRY cycling, INS editor behavior, and Alpha A-Z/θ coverage;
- Phase 3 evaluator precedence, arity/domain errors, complex behavior, and deferred-menu isolation;
- A-LOCK activation/cancellation, temporary 2nd restoration, compact-menu navigation/insertion,
  unavailable rows, origin returns, direct-view exits, and no-fallthrough behavior;
- whole-token Boolean cursor behavior plus angle-marker precedence, Angle Unit overrides,
  coordinate conversions, and disabled DMS rows;
- F1/F2 overlay origins, bottom-tab navigation, unavailable F3/F4 and calculus rows, structured
  fraction completion, context-sensitive Quit, exact-fraction preference, decimal override, and
  FUNC scalar domains;
- modifier cancellation and one-shot consumption;
- direct and view-specific state transitions;
- evaluator, persistence, history, graph, trace, and zoom edge cases.

Manual Minecraft smoke checks should cover:

- opening and closing the inventory repeatedly;
- calculator and toggle-button visibility;
- dragging and resetting position;
- clicking each edge of several scaled hitboxes;
- Home, Y=, Window, Mode, Zoom, Graph, and Trace rendering;
- repeated arrows, Enter, Clear, 2nd, and Alpha;
- closing the inventory during trace or interactive zoom;
- reopening after persistent data has been saved.

## Dependency rules

- `input` and `ui` must not depend on Minecraft classes.
- Controllers may depend on calculator domain stores, but not on `GuiGraphics` or `AbstractWidget`.
- Renderers may read controllers and stores, but must not route input.
- Persistent stores may depend on evaluation and `CalculatorPersistence`, but not on widgets.
- The widget may depend on all client-facing layers only as an adapter.

## Refactoring rules

- Keep behavior changes separate from structural moves whenever practical.
- Add characterization tests before moving behavior that lacks coverage.
- Extract one view or subsystem at a time and compile after each extraction.
- Do not combine an input refactor with a persistence-format migration.
- Treat approximately 700 lines as a review signal, not an automatic failure. Split a file when it
  owns unrelated reasons to change.
