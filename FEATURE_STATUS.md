# MI-84 Calculator Feature Status

This is the public, at-a-glance status of every physical calculator key in version 0.3. It is a
snapshot of the implementation; `BUTTON_MATRIX.md` is the maintainer-facing behavioral contract.

| Status | Meaning |
|---|---|
| **Implemented** | Available and usable in its supported calculator views. |
| **Partial** | The feature is usable, but some related rows, contexts, or functions are unavailable. |
| **Deferred** | Planned, but waiting on a larger subsystem or a reviewed interface. |
| **Not implemented** | The key has no feature on that layer, or its planned behavior is not available. |

| Physical button | Normal layer | Status | 2nd layer | Status | Alpha layer | Status |
|---|---|---|---|---|---|---|
| `Y=` | Y= equation editor (Y1–Y9) | Implemented | STAT PLOT configuration; Scatter/Line rendering | Partial | F1 FRAC function menu | Implemented |
| `Window` | Graph-window editor | Implemented | TBLSET | Implemented | F2 FUNC function menu | Partial |
| `Zoom` | Zoom and memory menu | Implemented | FORMAT | Partial | F3 MTRX menu | Deferred |
| `Trace` | Graph trace | Implemented | CALC graph-analysis menu | Deferred | F4 YVAR menu | Implemented |
| `Graph` | Graph view | Implemented | TABLE view | Implemented | F5 special menu | Deferred |
| `2nd` | One-shot 2nd modifier | Implemented | Cancel 2nd | Implemented | Switch Alpha to 2nd | Implemented |
| `Mode` | Persistent Mode editor | Implemented | Quit to Home | Implemented | No shifted function | Not implemented |
| `Del` | Forward delete | Implemented | Insert/overwrite toggle; named-list creation on a list header | Implemented | No shifted function | Not implemented |
| `Down` | Move/select down | Implemented | No shifted function | Not implemented | No shifted function | Not implemented |
| `Up` | Move/select up | Implemented | No shifted function | Not implemented | No shifted function | Not implemented |
| `Alpha` | One-shot Alpha modifier | Implemented | A-LOCK | Implemented | Cancel Alpha | Implemented |
| `X,T,θ,n` | Insert graph variable `X` | Implemented | LINK Send/Receive | Deferred | Fraction-template shortcut | Implemented |
| `Stat` | STAT menu and real-list editor | Partial | LIST names/operations/math | Partial | No shifted function | Not implemented |
| `Left` | Move/select left | Implemented | No shifted function | Not implemented | No shifted function | Not implemented |
| `Right` | Move/select right | Implemented | No shifted function | Not implemented | No shifted function | Not implemented |
| `Math` | MATH/NUM/CMPLX/PROB/FRAC menu | Partial | TEST/LOGIC/CONDITIONS menu | Partial | Insert `A` / Zoom shortcut | Implemented |
| `Apps` | Applications menu | Deferred | ANGLE menu | Partial | Insert `B` / Zoom shortcut | Implemented |
| `Prgm` | Program menu | Deferred | DRAW/POINTS/STO menu | Deferred | Insert `C` / Zoom shortcut | Implemented |
| `Vars` | Window/Zoom/Y-VARS menu | Partial | DISTR/DRAW menu | Deferred | No shifted function | Not implemented |
| `Clear` | Clear active editor or cancel operation | Implemented | No shifted function | Not implemented | No shifted function | Not implemented |
| `x⁻¹` | Reciprocal | Implemented | MATRIX menu | Deferred | Insert `D` / Zoom shortcut | Implemented |
| `sin` | `sin(` | Implemented | `sin⁻¹(` | Implemented | Insert `E` / Zoom shortcut | Implemented |
| `cos` | `cos(` | Implemented | `cos⁻¹(` | Implemented | Insert `F` / Zoom shortcut | Implemented |
| `tan` | `tan(` | Implemented | `tan⁻¹(` | Implemented | Insert `G` / Zoom shortcut | Implemented |
| `^` | Exponentiation | Implemented | Insert `π` | Implemented | Insert `H` | Implemented |
| `x²` | Square | Implemented | `sqrt(` | Implemented | Insert `I` | Implemented |
| `,` | Comma-separated scalar arguments | Partial | `EE` scientific exponent | Implemented | Insert `J` | Implemented |
| `(` | Opening parenthesis | Implemented | List opener `{` | Implemented | Insert `K` | Implemented |
| `)` | Closing parenthesis | Implemented | List closer `}` | Implemented | Insert `L` | Implemented |
| `÷` | Division | Implemented | Insert `e` | Implemented | Insert `M` | Implemented |
| `log` | `log(` | Implemented | `10^(` | Implemented | Insert `N` | Implemented |
| `7` | Digit / Zoom shortcut | Implemented | Sequence variable `u` | Deferred | Insert `O` | Implemented |
| `8` | Digit / Zoom shortcut | Implemented | Sequence variable `v` | Deferred | Insert `P` | Implemented |
| `9` | Digit / Zoom shortcut | Implemented | Sequence variable `w` | Deferred | Insert `Q` | Implemented |
| `×` | Multiplication | Implemented | Matrix opener `[` | Deferred | Insert `R` | Implemented |
| `ln` | `ln(` | Implemented | `e^(` | Implemented | Insert `S` | Implemented |
| `4` | Digit / menu shortcut | Implemented | List name `L4` | Implemented | Insert `T` | Implemented |
| `5` | Digit / Zoom shortcut | Implemented | List name `L5` | Implemented | Insert `U` | Implemented |
| `6` | Digit / Zoom shortcut | Implemented | List name `L6` | Implemented | Insert `V` | Implemented |
| `−` | Subtraction | Implemented | Matrix closer `]` | Deferred | Insert `W` | Implemented |
| `sto→` | Store expression in scalar variable | Implemented | Variable recall prompt | Deferred | Insert `X` | Implemented |
| `1` | Digit / menu shortcut | Implemented | List name `L1` | Implemented | Insert `Y` | Implemented |
| `2` | Digit / menu shortcut | Implemented | List name `L2` | Implemented | Insert `Z` | Implemented |
| `3` | Digit / menu shortcut | Implemented | List name `L3` | Implemented | Insert `θ` | Implemented |
| `+` | Addition | Implemented | MEM menu | Deferred | Quote `"` | Deferred |
| `On` | Wake/cancel calculator operation | Deferred | LCD off | Deferred | No shifted function | Not implemented |
| `0` | Digit / Zoom shortcut | Implemented | No shifted function | Not implemented | Space | Deferred |
| `.` | Decimal point | Implemented | Imaginary unit `i` | Implemented | Colon `:` | Deferred |
| `(−)` | Toggle operand sign | Implemented | Insert `Ans` | Implemented | Question mark `?` | Deferred |
| `Enter` | Submit/confirm/select | Implemented | ENTRY history recall | Implemented | Numeric Solver | Deferred |

| Feature area | Current status | What is available now | What remains unavailable or deferred |
|---|---|---|---|
| Core calculation | Implemented | Arithmetic, powers, reciprocal, parentheses, implicit multiplication, `Ans`, `π`, `e`, `i`, scientific notation capped at ±308, normalized common trig identities, stable real/rectangular-complex calculation, and history | General floating-point functions remain limited to Double precision |
| Functions | Partial | Trig/inverse trig, logs, roots, `abs`, `round`, integer helpers, `min`, `max`, `gcd`, `lcm`, remainder, `nPr`, `nCr`, factorial | Numerical calculus, solver, broader complex functions, and random generators |
| Fractions | Partial | Stacked fractions, mixed numbers, exact fractional Home results, fraction editing | Fraction/decimal and improper/mixed conversions |
| Variables | Implemented | Persistent A–Z and `θ` scalar variables, scalar storage, `X` graph coordinate, and typed graph-variable references | Matrices, strings, programs, and broader variable domains |
| Lists | Partial | Persistent real L1–L6 and named lists, STAT→Edit, literals, sorting, Fill, sequences, transforms, and summaries | Complex lists, Select, matrix conversion, and statistics calculations |
| Graphing | Partial | Y= and Window editors, colored graphing, trace, zoom, TBLSET/TABLE, FORMAT, and Scatter/Line plots | Graph CALC, drawing, distribution plots, and polar/sequence graph types |
| Menus | Partial | TEST/LOGIC, partial ANGLE and MATH families, STAT Edit, LIST, nested Window/Zoom/Y-VARS, STAT PLOT, and FRAC/FUNC/YVAR overlays | CONDITIONS templates, DMS, MTRX, APPS, STAT CALC/TESTS, PRGM, broader VARS domains, DISTR, MATRIX, and MEM |
| Editing | Implemented | Forward delete, cursor movement, sign editing in supported editors, visible insert mode, ENTRY history recall, and A-LOCK | Computer-keyboard expression entry is not supported |
| Persistence | Implemented | Calculation history, scalars, real lists, Y=, Window, TABLE SETUP, FORMAT, STAT PLOT, Mode, and zoom memory | Calculator overlay position across game restarts |
| Inventory integration | Implemented | Draggable inventory overlay, X-button toggle, configurable `N`/`H`/`K` controls | More complete resizing and configurable placement |
