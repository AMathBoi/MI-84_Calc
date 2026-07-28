The Minecraft Instruments 84: an all-in-one calculator for blocky calculations!

MI-84 Calculator adds a draggable graphing-calculator overlay to the Minecraft inventory.
Latest version is 0.2, launched on 7/28/26

Under development! Only 1.21.1 fabric for now, more versions will be launched in the future

## Using the calculator

Open an inventory, then click the `X` button at its bottom-left corner to show the calculator or use the keybind.
Drag the calculator from its top. The default controls, active only while an inventory is open,are:

- `N` — reset calculator position
- `H` — toggle calculator visibility
- `K` — toggle the visibility inventory `X` button

All three bindings can be changed in Minecraft's Controls menu under **MI-84 Calculator**.

Currently, the calculator includes all scientific functions, basic graphing features, and some more advanced features.

For a complete public status table covering every button and major feature area, see [Feature Status](FEATURE_STATUS.md).

## Known issues

- Some values that should evaluate to an exact number can contain small floating-point errors. For example, `sin(π)` may return a very small non-zero value instead of `0`.
- In the Y= editor, setting an equation to `Y=Ans` fails silently.
- In the Y= editor, entries made with the `2nd` modifier do not insert anything.
- The `2nd`-layer Insert function does not visually indicate insert mode. The cursor should continue blinking as an underscore over the character at the insertion point (for example, over `9`: `9` → `_` → `9`).
