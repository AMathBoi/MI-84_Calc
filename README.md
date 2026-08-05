The Minecraft Instruments 84: an all-in-one calculator for blocky calculations!

**Version 0.3** targets **fabric 1.21.1** and is client-side only. It's the latest development version.

## Dependencies

- Fabric API
- Fabric Language Kotlin

## Use

Open an inventory and click the `X` button at the bottom-left or the `H` Hotkey to show or hide the calculator. 

The default keybindings work only while an inventory is open:

- `N` — reset the calculator position
- `H` — toggle calculator visibility
- `K` — toggle the inventory `X` button

All three bindings can be changed under Controls.

## Version 0.3 highlights

- Scientific and rectangular-complex calculation with persistent history and scalar variables.
- Structured fractions, roots, combinatorics, TEST/LOGIC, partial ANGLE and MATH-family menus.
- Persistent real lists, named lists, STAT→Edit, and LIST NAMES/OPS/MATH operations.
- Y=, Window, trace, zoom, graph TABLE/TBLSET, and renderer-backed FORMAT settings.
- Persistent STAT PLOT configuration with Scatter and Line rendering.

![Home view demo](https://cdn.modrinth.com/data/cached_images/06423470f6a3ffef7451f0f7b4c01114e9c96ead_0.webp)

![Graph view demo](https://cdn.modrinth.com/data/cached_images/95308680e512e425e868199b859085f6f713652e_0.webp)

## Current limitations

- Input uses the calculator's on-screen keys; computer-keyboard expression entry is not supported.
- Polar and sequence graph modes, graph CALC tools, matrices, programming, and statistical
  calculations remain deferred.
- Statplot Histogram, box-plot, and relative-frequency settings are visible as `[deferred]`; only Scatter
  and Line currently render.
- The overlay may be clipped at unusually small GUI sizes

See [Feature Status](FEATURE_STATUS.md) for the complete public implementation table and
[Changelog](CHANGELOG.md) for release details.
