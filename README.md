The Minecraft Instruments 84: an all-in-one calculator for blocky calculations!

MI-84 Calculator adds a draggable graphing-calculator overlay to the Minecraft inventory.
Version 0.2 targets Minecraft 1.21.1 with Fabric Loader, Fabric API, and Fabric Language Kotlin.

## Using the calculator

Open an inventory, then click the `X` button at its bottom-left corner to show the calculator.
Drag the calculator from its top strip. The default controls, active only while an inventory is open,
are:

- `N` — reset calculator position
- `H` — toggle calculator visibility
- `K` — toggle the inventory `X` button

All three bindings can be changed in Minecraft's Controls menu under **MI-84 Calculator**.

The calculator includes expression history, persistent scalar variables, Y= and Window editors,
graphing and trace, zoom controls, real and rectangular-complex calculation, fractions, and the
implemented TEST, ANGLE, MATH, NUM, CMPLX, PROB, FRAC, and function-menu entries. Some visible
calculator functions are intentionally unavailable while they are still under development.

For a complete public status table covering every button and major feature area, see
[Feature Status](FEATURE_STATUS.md).

## Known issues

- Some values that should evaluate to an exact number can contain small floating-point errors. For example, `sin(π)` may return a very small non-zero value instead of `0`.
- In the Y= editor, setting an equation to `Y=Ans` fails silently.
- In the Y= editor, entries made with the `2nd` modifier do not insert anything.
- The `2nd`-layer Insert function does not visually indicate insert mode. The cursor should continue blinking as an underscore over the character at the insertion point (for example, over `9`: `9` → `_` → `9`).
