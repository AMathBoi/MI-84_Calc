# Changelog

All notable changes to MI-84 Calculator are documented here.

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

### Known issues

- Insert mode has no visual indicator.
- The fixed-size overlay can be clipped at unusually small GUI dimensions and is not clamped after
  a resize.
- Stored Mode choices whose owning graph/statistics/exact-answer systems are deferred do not yet
  alter behavior.
