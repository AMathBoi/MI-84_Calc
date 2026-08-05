# Changelog

All notable changes to MI-84 Calculator are documented here.

## 0.3 - 2026-08-04

### Added

- Persistent real `L1`–`L6` and user-named lists, a TI-84-style STAT→Edit table, list literals, and
  LIST NAMES/OPS/MATH operations including sequences, sorting, Fill, and summaries.
- Persistent TABLE SETUP and graph TABLE views with Auto/Ask independent and dependent values,
  signed row scrolling, editable Y headers, and visible error cells.
- Persistent FORMAT controls for rectangular coordinates, trace readouts, grid, axes, labels, and
  expression visibility.
- Persistent STAT PLOT configuration for three fixed-color plots, including Scatter and Line graph
  rendering with selectable marks and clipped line segments.

### Changed

- `Xres` now accepts only exact integer values from 1 through 8, matching the TI-84 range.
- The mod is declared client-only in Fabric metadata.
- Public and maintainer documentation now distinguish available list, table, format, and plot
  features from distribution plots, polar graphing, statistics, and other deferred systems.

### Fixed

- A full 999-element list no longer exposes or attempts to commit an invalid 1,000th element.
- Complex division remains stable when finite operands have extremely large or small magnitudes;
  genuine overflow and underflow now produce explicit calculator errors.
- `seq(` substitutes only the selected scalar variable token and no longer corrupts identifiers
  such as `Ans`, `logBASE`, graph variables, or list names.
- Insert mode now uses a blinking underscore cursor while preserving the character beneath it.

### Known limitations

- Histogram, both box-plot types, and relative-frequency plot configuration are visible but remain
  marked `[deferred]`; Scatter and Line are the rendered STAT PLOT types in 0.3.
- Polar/sequence graphing, graph CALC, matrices, statistics calculations, programs, apps, linking,
  and several dependent menu rows remain unavailable.
- The overlay can be clipped at unusually small GUI dimensions and is not clamped after resizing.
- Calculator position is retained between inventory sessions but not across game restarts.
- Recalling a history input longer than the 31-character editor limit fails without an on-screen
  error.

## 0.2 - 2026-07-28

### Added

- In-inventory graphing with configurable Window settings, trace navigation, and Zoom/Memory tools.
- Persistent calculation history, scalar variables, Y= equations, Window settings, Mode settings,
  and zoom memory.
- Real and rectangular-complex expression evaluation, including angle-aware trigonometry,
  scientific/engineering/fixed display modes, `Ans`, and implicit multiplication.
- Structured fraction and mixed-number entry, roots, logarithms, permutations, combinations, and
  factorial.
- TEST/LOGIC, ANGLE, MATH/NUM/CMPLX/PROB/FRAC compact menus and FRAC/FUNC bottom-tab overlays.
- Configurable inventory-only controls for calculator visibility, its toggle button, and position
  reset.

### Changed

- Visible but unfinished calculator operations are explicitly unavailable instead of falling back
  to a different key action.
- Named Y-function insertions now use distinct Y₁–Y₉ tokens while existing raw `Y1`–`Y9` saves
  remain compatible; scalar `Y` followed by a digit stays scalar multiplication.
- Scientific `EE` exponents are limited to ±308, and floating-function conversion reports an
  explicit range error instead of silently underflowing a nonzero value to zero.

### Fixed

- Common 30°/45°/60°/90° forward and inverse trigonometric identities now normalize
  deterministically; tangent at odd multiples of 90° reports a domain error.
- Graph sampling checks actual midpoints so pole-like discontinuities are not joined as asymptotes.
- The `(−)` key now toggles the active operand in Y= and Window as well as Home.
- F4 YVAR, nested VARS, direct fraction-template, and shifted Y= status documentation now matches
  the implementation.

### Known issues at release

- Insert mode has no visual indicator.
- The fixed-size overlay can be clipped at unusually small GUI dimensions and is not clamped after
  a resize.
- Stored Mode choices whose owning graph/statistics/exact-answer systems are deferred do not yet
  alter behavior.
