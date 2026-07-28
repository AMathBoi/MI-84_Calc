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

- The calculator remains a client-side, draggable inventory overlay with crisp pixel-art rendering.
- Visible but unfinished calculator operations are explicitly unavailable instead of falling back
  to a different key action.

### Known issues

- Some exact results may display small floating-point residue, such as `sin(π)`.
- `Y=Ans` fails silently in the Y= editor.
- `2nd`-modified entries do not insert in the Y= editor.
- Insert mode has no visual indicator.
