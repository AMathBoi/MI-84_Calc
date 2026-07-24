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
- Primary commands are exhaustive. Phase 1 scalar 2nd meanings, Phase 2 ENTRY/INS, and Alpha
  A-Z/θ have explicit typed commands; remaining unimplemented 2nd and Alpha meanings are explicit
  placeholders.

Visible legends are not identifiers. Code should compare `CalculatorKey.SIN`, not `"sin"`, and
should never infer behavior from displayed text.

### Controller and UI state

- `controller/CalculatorController.kt` is the single input-dispatch entry point.
- `controller/CalculatorViewControllers.kt` contains Home, Y=, Window, and Mode input behavior.
- `controller/DispatchResult.kt` distinguishes handled input, unsupported primary keys, and planned
  modifier placeholders.
- `ui/CalculatorUiState.kt` owns transient state: active view, modifier layer, history/ENTRY
  selection, insert/overwrite mode, trace state, and interactive zoom state.

Persistent calculator content does not belong in `CalculatorUiState`.

### Rendering

- `CalculatorRenderer` reads controller state and persistent stores to draw non-graph LCD views.
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
- `YEqualsMemory`, `WindowSettingsMemory`, `ModeSettingsMemory`, and `ZoomMemory` own their respective
  persistent domains.
- `CalculatorPersistence` is the only general file-writing boundary. New stores must use it rather
  than writing files directly.
- Existing file formats must remain backward compatible unless a documented migration and regression
  test are added.

## Input resolution rules

For every input event, the controller resolves behavior in this order:

1. The physical `2nd` or `Alpha` modifier key.
2. Active-view modifier overrides, currently physical Zoom Alpha A-G shortcuts. Other Alpha
   variables remain typed commands and are ignored by views that do not edit expressions.
3. The command from the active Normal, 2nd, or Alpha layer.
4. Direct view transitions such as Mode, Window, Graph, Trace, and 2nd Mode Quit.
5. The active view controller.

2nd and Alpha are one-shot layers. Pressing the same modifier twice cancels it. A non-modifier key
consumes the layer even when its mapping is only a placeholder. Placeholders must not fall through to
the primary command.

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
