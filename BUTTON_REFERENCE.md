# MI-84 Button Behavior Reference

This document explains what each physical button is intended to do, how it is used, and what each
menu contains. `BUTTON_MATRIX.md` remains the compact authoritative status table. This reference
does not claim that planned or deferred behavior is currently implemented.

## General input rules

- Normal is the default layer.
- `2nd` selects the blue legend for one non-modifier key. Pressing `2nd` again cancels it.
- `Alpha` selects the green legend for one non-modifier key. Pressing `Alpha` again cancels it.
- `2nd` then `Alpha` enables persistent A-LOCK, shown as a prefix in the gray mode header. Pressing
  Alpha again cancels it.
- Pressing the other modifier switches layers: Alpha then `2nd` selects 2nd, and 2nd then `Alpha`
  enables A-LOCK rather than silently acting like one-shot Alpha. A temporary 2nd command while
  A-LOCK is active returns to A-LOCK afterward.
- A non-modifier key always consumes the active one-shot layer, including an unavailable or
  no-function mapping. Shifted input never falls through to Normal.
- Menus use left/right to change tabs, up/down to move, Enter to select, Clear to cancel, and their
  displayed number or letter as a direct hotkey.
- A menu item ending in `...` opens another screen or parameter wizard.
- Every new menu, screen, prompt, template, indicator, and full-screen presentation is deferred
  until a visual review.

## Deferred-surface dependency index

The detailed sections below define the tabs, items, and use of each planned surface. This index
records the prerequisite subsystem and confirms the visual gate without repeating each item list.

| Surface | Required foundation | Visual-review rule |
|---|---|---|
| STAT EDIT/CALC/TESTS | Lists first; statistics and parameter validation per operation | Review the list editor, each menu, and each parameter wizard separately |
| MATH/NUM/CMPLX/PROB/FRAC | Evaluator primitives; MathPrint only for templates that need it | Review tabs, unavailable rows, and every template before exposure |
| APPS | A deliberately designed application storage and lifecycle, or an explicit unsupported decision | First review the empty state; review launching only after the subsystem exists |
| PRGM EXEC/EDIT/NEW and command tabs | Program and string storage, editor, interpreter, and I/O model | Review program lists, editor states, and command tabs separately |
| VARS/Y-VARS and F4 YVAR | Only typed variables backed by an implemented memory/graph domain | Review category tabs, unavailable rows, and selection return behavior |
| STAT PLOT | Stable lists and graph-plot rendering | Review the main menu and each plot editor/type separately |
| TBLSET and TABLE | Table settings/evaluation using graph functions | Review settings, Auto/Ask entry, columns, scrolling, and error cells separately |
| FORMAT | Graph renderer support for every option shown | Do not display decorative settings that have no runtime effect |
| Graph CALC | Numerical graph-analysis primitives and interaction state | Review each operation's prompts, markers, cancellation, and errors separately |
| INS indicator | Insert/overwrite editor state | INS may ship without an indicator; any indicator requires review |
| A-LOCK indicator | General scalar Alpha input | Reviewed and implemented 2026-07-26 |
| LINK | A safe transfer format and local or remote transport | Review SEND and RECEIVE only after the transport is defined |
| LIST | Typed list values, storage, evaluator operations, and editor | Review NAMES/OPS/MATH and list-editor states separately |
| TEST/LOGIC/CONDITIONS | Relational and numeric-Boolean evaluator support | TEST/LOGIC reviewed and implemented; CONDITIONS remains unavailable pending editable-template review |
| ANGLE | Angle tokens/conversions and current angle-mode semantics | Reviewed; degree/radian markers and coordinate conversions implemented, DMS rows unavailable |
| DRAW/POINTS/STO | Graph overlay state plus picture/GDB storage for STO | Review drawing interactions separately from storage menus |
| DISTR/DRAW | Numerically validated distribution functions; graph shading for DRAW | Review numeric and shading tabs separately; expose only stable operations |
| MATRIX | Typed matrices, evaluator rules, storage, and editor | Review NAMES, MATH, EDIT, and MathPrint templates separately |
| RCL | The typed variable domains offered by the prompt | Review selection, unavailable types, and insertion behavior |
| MEM | Persistence ownership and recovery behavior for each item | Review reporting, confirmation, destructive, archive, and grouping flows separately |
| OFF/ON | LCD power state distinct from overlay visibility | Review wake, cancellation, header, and inventory reopen behavior |
| F1/F2/F3 and Alpha fraction template | Approved token/template primitives; matrices for F3 | F1/F2 overlay and FRAC templates reviewed and implemented; F3/F4 and direct Alpha fraction shortcut remain separately gated |
| F5 SPECIAL | A specific implemented context such as graph interaction or program editing | Define that context's exact items here, then review it before implementation |
| Numeric Solver / Alpha Enter SOLVE | Solver domain and reviewed Numeric Solver screen | SOLVE remains unavailable outside the solver |

## Normal layer

### Graph and view keys

| Key | Behavior and use |
|---|---|
| Y= | Opens the existing editor for `Y1` through `Y9`. Arrows select and edit equations. |
| Window | Opens the existing graph-window editor. Arrows select settings and entry keys edit them. |
| Zoom | Opens the existing ZOOM/MEMORY view. Number and Alpha A-G hotkeys select operations. |
| Trace | Opens Graph with a trace cursor. Left/right change X; up/down change active equation. |
| Graph | Opens the existing graph view and plots all enabled, non-empty Y= expressions. |
| Mode | Opens the existing persistent Mode editor. |

### Editing and navigation keys

| Key | Behavior and use |
|---|---|
| 2nd | Activates or cancels the one-shot 2nd layer. |
| Del | Forward-deletes the token at the editor cursor. |
| Down / Up | Move selections, history, menu rows, settings, equations, or traced functions. |
| Alpha | Activates or cancels the one-shot Alpha layer. |
| X,T,θ,n | Currently inserts `X`. Future graph modes may choose `T`, `θ`, or `n` contextually. |
| Left / Right | Move an edit cursor, option selection, menu tab, trace point, or zoom cursor. |
| Clear | Clears active input or cancels the current operation. Empty Home input hides visible history. |
| sto→ | Inserts `→`; follow it with Normal X or Alpha A–Z/θ to store a scalar value. |
| On | Future wake/cancel key. Its LCD presentation is deferred for visual review. |
| Enter | Submits an expression, confirms a selection, or recalls a selected valid history row. |

### Direct expression keys

| Keys | Behavior and use |
|---|---|
| `0`–`9` | Insert digits. In an existing menu, physical numeric hotkeys may select items. |
| `+`, `−`, `×`, `÷`, `^` | Insert binary operators. Empty Home input begins an `Ans` expression where supported. |
| `.` | Inserts a decimal point. |
| `(−)` | Toggles the sign of the current operand; it is not the subtraction operator. |
| `(` and `)` | Insert grouping parentheses. |
| `,` | Inserts a comma. It separates arguments in the implemented scalar functions; unsupported function families remain deferred. |
| `x²` | Squares the current operand or `Ans`. |
| `x⁻¹` | Applies a reciprocal to the current operand or `Ans`. |
| `sin`, `cos`, `tan`, `log`, `ln` | Insert the named function and an opening parenthesis. |

### Scalar variables

- `A`–`Z` and `θ` are persistent scalar variables. Every variable defaults to real zero, so there
  is no undefined-variable state.
- Store with `expression→variable`; for example, `5→A` and `i→B`. Real mode accepts real values,
  while `a+bi` also stores rectangular-complex values.
- Scalar values are saved in `config/mi84_calc_scalar_variables.txt` through
  `CalculatorPersistence`.
- X remains the normal graph variable. Its real component continues to be mirrored in the legacy
  `x` line of `mi84_calc_display_memory.txt`; a legacy X is imported when the scalar file has none.
- During graph evaluation, X is the sampled graph coordinate rather than its stored Home value.
  Other real scalar variables are available to equations. A complex-valued variable makes that
  graph evaluation undefined.
- Juxtaposed variables use implicit multiplication, such as `2A` and `AX`.

### Implemented nonvisual evaluator foundations

Phase 3 added expression primitives before exposing their deferred menus. Phase 4 now provides the
reviewed TEST/LOGIC, partial ANGLE, and partial MATH-family entry paths; DISTR remains deferred.

- Arithmetic is evaluated before `=`, `≠`, `>`, `≥`, `<`, and `≤`. Relations are evaluated before
  `and`; `or` and `xor` share the next precedence level and evaluate left to right. A relation or
  Boolean operation returns numeric `1` or `0`.
- Zero is false and every nonzero real or rectangular-complex value is true. Complex `=` and `≠`
  compare both components; ordered comparisons of complex values report a syntax error.
- Function calls accept comma-separated arguments and reject missing arguments, stray top-level
  commas, and unsupported arities. Omitted trailing function parentheses are still completed.
- `and`, `or`, and `xor` move, overwrite, and forward-delete as whole editor tokens.
- `abs(x)` returns an absolute value or complex magnitude. `round(x)` rounds to ten significant
  digits and `round(x,n)` rounds to 0–9 decimal places; both use half-up rounding.
- `iPart(x)` truncates toward zero, `fPart(x)` returns `x-iPart(x)`, and `int(x)` floors.
- `min` and `max` accept exactly two scalar arguments. `gcd` and `lcm` require two nonnegative
  integers no larger than `1E12`. `remainder(a,b)` requires a nonnegative whole-number dividend and
  a positive whole-number divisor.
- Probability primitives remain deferred until a numerical library and accuracy strategy receive
  separate approval. MathPrint templates and every menu presentation remain behind visual review.

## Unsupported Normal menus

These physical buttons are currently explicit unsupported commands. Their intended Normal behavior
is documented here and deferred according to `BUTTON_MATRIX.md`.

### STAT

STAT opens a three-tab statistics menu.

#### EDIT

| Item | Behavior |
|---|---|
| `1:Edit...` | Opens the list editor. |
| `2:SortA(` | Sorts one or more lists in ascending order. |
| `3:SortD(` | Sorts one or more lists in descending order. |
| `4:ClrList` | Clears the named lists. |
| `5:SetUpEditor` | Chooses which lists appear in the list editor. |

#### CALC

| Item | Behavior |
|---|---|
| `1:1-Var Stats` | One-variable summary statistics. |
| `2:2-Var Stats` | Paired two-variable summary statistics. |
| `3:Med-Med` | Median-median regression. |
| `4:LinReg(ax+b)` | Linear regression in slope-intercept order. |
| `5:QuadReg` | Quadratic regression. |
| `6:CubicReg` | Cubic regression. |
| `7:QuartReg` | Quartic regression. |
| `8:LinReg(a+bx)` | Linear regression in intercept-slope order. |
| `9:LnReg` | Logarithmic regression. |
| `0:ExpReg` | Exponential regression. |
| `A:PwrReg` | Power regression. |
| `B:Logistic` | Logistic regression. |
| `C:SinReg` | Sinusoidal regression. |
| `D:Manual-Fit` | Manually fits an equation to a plot. |

#### TESTS

| Item | Behavior |
|---|---|
| `1:Z-Test...` | One-sample Z hypothesis test. |
| `2:T-Test...` | One-sample t hypothesis test. |
| `3:2-SampZTest...` | Two-sample Z test. |
| `4:2-SampTTest...` | Two-sample t test. |
| `5:1-PropZTest...` | One-proportion Z test. |
| `6:2-PropZTest...` | Two-proportion Z test. |
| `7:ZInterval...` | One-sample Z confidence interval. |
| `8:TInterval...` | One-sample t confidence interval. |
| `9:2-SampZInt...` | Two-sample Z interval. |
| `0:2-SampTInt...` | Two-sample t interval. |
| `A:1-PropZInt...` | One-proportion Z interval. |
| `B:2-PropZInt...` | Two-proportion Z interval. |
| `C:χ²-Test...` | Chi-square independence test. |
| `D:χ²GOF-Test...` | Chi-square goodness-of-fit test. |
| `E:2-SampFTest...` | Two-sample F test. |
| `F:LinRegTTest...` | t test for a linear-regression slope. |
| `G:LinRegTInt...` | confidence interval for a linear-regression slope. |
| `H:ANOVA(` | One-way analysis of variance. |

### MATH

MATH opens five tabs. Selecting a function normally pastes a token or template into the active
expression. The longer MATH and NUM tabs use the shared nine-row scrolling viewport. Numeric
hotkeys select `0`–`9`; displayed `A`–`D` rows use Alpha plus the corresponding physical key.

#### MATH

1. `►Frac`
2. `►Dec`
3. cube
4. cube root
5. nth root
6. `fMin(`
7. `fMax(`
8. `nDeriv(`
9. `fnInt(`
0. `Σ(`
A. `logBASE(`
B. `piecewise(`
C. `Numeric Solver...`

#### NUM

1. `abs(`
2. `round(`
3. `iPart(`
4. `fPart(`
5. `int(`
6. `min(`
7. `max(`
8. `lcm(`
9. `gcd(`
0. `remainder(`
A. improper/mixed-fraction conversion
B. fraction/decimal conversion
C. mixed-number template
D. fraction template

#### CMPLX

1. `conj(`
2. `real(`
3. `imag(`
4. `angle(`
5. `abs(`
6. `►Rect`
7. `►Polar`

#### PROB

1. `rand`
2. `nPr`
3. `nCr`
4. factorial `!`
5. `randInt(`
6. `randNorm(`
7. `randBin(`
8. `randIntNoRep(`

#### FRAC

1. fraction template
2. mixed-number template
3. fraction/decimal conversion
4. improper/mixed-fraction conversion

The implemented menu follows the approved availability review:

- **MATH**: cube, cube root, nth root, and `logBASE(` are available. Conversion, function
  minimization/maximization, numerical calculus, summation, piecewise, and Numeric Solver rows are
  visible but unavailable. Square, cube, and nth roots display as radicals with a small gap before
  the radicand and an overbar that grows with it. Their visual weight is slightly enlarged to match
  a structured fraction. Only an empty indexed-root index has a dotted placeholder; the radicand
  remains unboxed. Right advances from the index to the radicand and then exits the completed
  notation.
- **NUM**: the ten scalar helpers and both shared fraction templates are available. The two
  conversion rows remain unavailable.
- **CMPLX**: `abs(` is available. Complex component/angle and rectangular/polar conversion rows
  remain unavailable until their owning evaluator/display behavior is approved.
- **PROB**: `nPr(`, `nCr(`, and factorial are available. Permutations and combinations display
  their two arguments as compact lowered operands around an enlarged `P` or `C`, matching the
  fraction's visual weight. Only empty operands have dotted placeholders. The blinking cursor is
  reduced and lowered within each operand; Left/Right cross the comma/closing delimiter as one
  visual field transition, and Right exits after the completed second operand. Raw function names
  remain internal. Random-number rows remain unavailable.
- **FRAC**: the shared fraction and mixed-number templates are available. Conversion rows remain
  unavailable.

### APPS

APPS opens a scrollable list of installed applications. MI-84 has no application framework yet, so
the first reviewed design should show `No applications installed` rather than pretend that TI apps
are functional. Application launching, storage, and lifecycle are a separate deferred subsystem.

### PRGM

Outside a program editor, PRGM contains:

- **EXEC**: list and run saved programs.
- **EDIT**: list and edit saved programs.
- **NEW**: `1:Create New`.

Inside a future program editor it changes to command tabs:

- **CTL**: `If`, `Then`, `Else`, `For(`, `While`, `Repeat`, `End`, `Pause`, `Lbl`, `Goto`,
  `Wait`, `IS>(`, `DS<(`, `Menu(`, `prgm`, `Return`, `Stop`, `DelVar`, and graph-style commands.
- **I/O**: `Input`, `Prompt`, `Disp`, `DispGraph`, `DispTable`, `Output(`, `getKey`, and
  `ClrHome`.
- **EXEC**: inserts another program call.
- **COLOR/HUB**: remain unavailable unless those systems are deliberately adopted.

### VARS

VARS opens variable-selection menus and pastes the chosen typed variable into the active editor.

- **VARS** categories: Window, Zoom, GDB, Picture, Image, String, Table, and Statistics.
- **Y-VARS** categories: Function (`Y1`–`Y9`, `Y0`), Parametric, Polar, On/Off, and future
  sequence/background categories.

Initially, only variables backed by existing MI-84 memory should be selectable. Unimplemented
variable types may be shown as unavailable after the menu receives visual approval.

## 2nd layer

### Graph-related screens

#### 2nd Y=: STAT PLOT

Main items:

1. `Plot1...`
2. `Plot2...`
3. `Plot3...`
4. `PlotsOn`
5. `PlotsOff`

Each plot editor contains On/Off, plot type, source lists, mark style, and color. Plot types are
Scatter, xyLine, Histogram, modified box plot, regular box plot, and normal probability plot.

#### 2nd Window: TBLSET

Contains:

- `TblStart`
- `ΔTbl`
- `Indpnt: Auto / Ask`
- `Depend: Auto / Ask`

Auto generates X values from `TblStart` and `ΔTbl`. Ask allows the user to enter X values.

#### 2nd Zoom: FORMAT

Contains:

- `RectGC / PolarGC`
- `CoordOn / CoordOff`
- `GridOff / GridDot / GridLine`
- `AxesOn / AxesOff`
- `LabelOff / LabelOn`
- `ExprOn / ExprOff`
- optional graph-color and asymptote choices only if supported by MI-84

#### 2nd Trace: CALC

| Item | Use |
|---|---|
| `1:value` | Enter X and evaluate enabled functions. |
| `2:zero` | Choose lower bound, upper bound, and guess for a root. |
| `3:minimum` | Choose bounds and a guess for a local minimum. |
| `4:maximum` | Choose bounds and a guess for a local maximum. |
| `5:intersect` | Choose two functions and a guess for their intersection. |
| `6:dy/dx` | Choose a function and X for a numerical derivative. |
| `7:∫f(x)dx` | Choose a function and integration bounds. |

#### 2nd Graph: TABLE

Displays an X column and one column for every enabled Y= expression. TBLSET controls automatic or
asked-for X values. Invalid evaluations display an undefined/error cell without closing the table.

### Editing, system, and data menus

#### 2nd Mode: QUIT

Returns to Home from any view and clears transient selection/navigation state. This is complete.

#### 2nd Del: INS

Toggles insert versus overwrite editing. Insert mode adds before the cursor; overwrite mode replaces
the token under the cursor in Home, Y=, and Window. The mode is transient, starts in overwrite, and
persists while moving between those views. Phase 2 deliberately adds no visual indicator; any future
indicator must be approved separately.

#### 2nd Alpha: A-LOCK

Enables persistent Alpha input until Alpha is pressed again. The gray header begins with `A-LOCK`
while it is active. Non-modifier Alpha commands and placeholders do not consume the lock. Pressing
2nd temporarily selects one 2nd command, then returns to A-LOCK. Compact-menu navigation, numeric
hotkeys, and direct view exits remain physical controls while locked.

#### 2nd X,T,θ,n: LINK

- **SEND**: choose typed variables to transmit.
- **RECEIVE**: wait for and accept a transfer.

This remains deferred until MI-84 has a defined local or remote transfer mechanism.

#### 2nd Stat: LIST

**NAMES** contains `L1`–`L6` and user-defined list names.

**OPS** contains `SortA(`, `SortD(`, `dim(`, `Fill(`, `seq(`, `cumSum(`, `ΔList(`,
`Select(`, `augment(`, `List►matr(`, `Matr►list(`, and supported numerator/denominator
operations.

**MATH** contains `min(`, `max(`, `mean(`, `median(`, `sum(`, `prod(`, `stdDev(`,
and `variance(`.

#### 2nd Math: TEST

This reviewed compact menu is implemented. Left/right changes tabs, up/down changes rows, Enter or
a displayed numeric hotkey pastes an available token and returns to the originating Home, Y=, or
Window editor, and Clear returns without editing. When opened from another view, selection returns
to Home. Direct view keys cancel the menu and open their view.

**TEST**

1. `=`
2. `≠`
3. `>`
4. `≥`
5. `<`
6. `≤`

Relations return numeric Boolean `1` or `0`.

**LOGIC**

1. `and`
2. `or`
3. `xor`
4. `not(`

Zero is false and nonzero is true.

**CONDITIONS**

The CONDITIONS tab pastes interval templates into a piecewise condition:

1. `lower < X < upper`
2. `lower < X ≤ upper`
3. `lower ≤ X < upper`
4. `lower ≤ X ≤ upper`
5. outside/open interval
6. outside/closed or mixed-endpoint interval

Internally, an interval is evaluated as its equivalent relations joined with `and` or `or`.
The rows are currently visible but unavailable; selecting one leaves the menu open. Their editable
multi-field template behavior requires a separate visual approval.

#### 2nd Apps: ANGLE

This reviewed single-tab compact menu shows all eight rows at once. It uses the same arrows,
numeric-hotkey, Enter, Clear, origin-return, A-LOCK, and direct-view behavior as TEST. There is no
bottom navigation guide.

1. degree marker `°`
2. DMS minute marker `'` — visible but unavailable
3. radian marker `ʳ`
4. `►DMS` — visible but unavailable
5. `R►Pr(`
6. `R►Pθ(`
7. `P►Rx(`
8. `P►Ry(`

Postfix `°` interprets the preceding value as degrees and postfix `ʳ` interprets it as radians,
converting to the active Angle Unit for the surrounding expression. Parenthesize a compound angle
before applying a marker, such as `(π/2)ʳ`. `R►Pr(x,y)` returns radius, `R►Pθ(x,y)` returns the polar
angle in the active Angle Unit, and `P►Rx(r,θ)` / `P►Ry(r,θ)` interpret θ in that unit. Minute entry
and DMS output remain unavailable until the complete degree/minute/second grammar and presentation
are approved.

#### 2nd Prgm: DRAW

**DRAW** contains `ClrDraw`, `Line(`, `Horizontal`, `Vertical`, `Tangent(`, `DrawF`,
`Shade(`, `DrawInv`, `Circle(`, `Text(`, and `Pen`.

**POINTS** contains `Pt-On(`, `Pt-Off(`, `Pt-Change(`, `Pxl-On(`, `Pxl-Off(`,
`Pxl-Change(`, and `pxl-Test(`.

**STO** contains `StorePic`, `RecallPic`, `StoreGDB`, and `RecallGDB`.

#### 2nd Vars: DISTR

**DISTR** contains `normalpdf(`, `normalcdf(`, `invNorm(`, `tpdf(`, `tcdf(`, `invT(`,
`χ²pdf(`, `χ²cdf(`, `Fpdf(`, `Fcdf(`, `binompdf(`, `binomcdf(`, `poissonpdf(`,
`poissoncdf(`, `geometpdf(`, and `geometcdf(`.

**DRAW** contains `ShadeNorm(`, `Shade_t(`, `Shadeχ²(`, and `ShadeF(`.

#### 2nd x⁻¹: MATRIX

- **NAMES**: `[A]` through `[J]`.
- **EDIT**: `[A]` through `[J]`, opening the selected matrix editor.
- **MATH**: determinant, transpose, dimension, fill, identity, random matrix, augment,
  matrix/list conversion, cumulative sum, row-echelon forms, row swap, row addition,
  row scaling, and scaled-row addition.

#### 2nd sto→: RCL

Opens a variable-selection prompt, then pastes the selected variable's current value into the active
editor. It must not erase or overwrite the stored variable.

#### 2nd +: MEM

1. `About`
2. `Mem Management/Delete...`
3. `Clear Entries`
4. `ClrAllLists`
5. `Archive...`
6. `UnArchive...`
7. `Reset...`
8. `Group...`
9. `Ungroup...`

Destructive items require confirmation and must be implemented separately from persistence-format
changes.

#### 2nd On: OFF

Turns off the LCD while retaining calculator memory. Normal On wakes it. Closing the Minecraft
inventory and turning off the calculator are different operations.

### Direct 2nd entry functions

The Phase 1 scalar entries—`Ans`, `i`, `π`, `e`, inverse trig, exponential functions, `sqrt(`, and
`EE`—are implemented in Home, Y=, and Window. The evaluator retains linear internal tokens while
approved roots use nonlinear LCD notation; the other rows retain the status recorded in
`BUTTON_MATRIX.md`.

| Physical key | 2nd result | Use |
|---|---|---|
| sin / cos / tan | `sin⁻¹(` / `cos⁻¹(` / `tan⁻¹(` | Inverse trig using Degree or Radian mode. Real-domain failures use principal complex values only in `a+bi` mode. |
| ^ | `π` | Inserts the pi constant and supports implicit multiplication such as `2π`. |
| x² | `sqrt(` | Inserts a principal square-root function displayed as an adaptive radical; negative real inputs require `a+bi`. |
| comma | `EE` | Appends to a completed decimal mantissa, such as `1.2EE5`. `(−)` negates the exponent; malformed or excessively large exponents report an error. |
| `(` / `)` | `{` / `}` | Opens/closes a list literal after list support exists. |
| ÷ | `e` | Inserts Euler's constant and supports implicit multiplication. |
| log | `10^(` | Inserts base-10 exponential; its omitted trailing parenthesis is completed on evaluation. |
| 7 / 8 / 9 | `u` / `v` / `w` | Inserts sequence variables after sequence support exists. |
| × / − | `[` / `]` | Opens/closes matrix notation after matrix support exists. |
| ln | `e^(` | Inserts natural exponential; its omitted trailing parenthesis is completed on evaluation. |
| 1–6 | `L1`–`L6` | Inserts a built-in list variable after list support exists. |
| . | `i` | Inserts the imaginary unit. Real mode rejects complex-only results; `a+bi` supports arithmetic and implicit multiplication such as `2i`. |
| (−) | `Ans` | Explicitly inserts the most recent valid raw real or rectangular-complex answer, skipping invalid history rows. |
| Enter | ENTRY | In Home, replaces the active edit line with the newest submitted input. Repeated ENTRY walks backward and stops at the oldest retained input. A different Home command resets the cycle; ENTRY cancels up/down history selection. |

## Alpha layer

### F1–F5 function menus

F1 and F2 use an approved function-menu overlay rather than the full-view compact menu used by
TEST and ANGLE. The active calculator view remains visible behind a boxed option list. The active
tab is shown along the bottom beside `FRAC`, `FUNC`, `MTRX`, and `YVAR`; Left and Right switch tabs.
The option box grows with the active tab, so FRAC leaves most of the underlying view visible while
FUNC uses most of the LCD.

Home supports all four tabs. F1/F2 opened from another typeable view, currently Y= or Window, remain
over that view and paste into its active editor. Opening them from a non-typeable view first returns
to Home. `2nd` + Mode (Quit) closes this overlay without leaving its retained Home, Y=, or Window
view. F3 MTRX and F4 YVAR remain visible tab placeholders until their later implementation.

- **F1 FRAC**: fraction and mixed-number templates are available. The improper/mixed and
  fraction/decimal conversion rows remain visible but unavailable. Template entry uses stacked
  whole/numerator/denominator fields inline at the active text cursor. Fraction digits are smaller
  than ordinary entry text, and the complete stacked fraction is about 50% taller than a normal
  number. Completed templates and reduced answers keep the visible fraction bar instead of exposing
  internal `frac(x,y)` text. A completed structured fraction is one cursor/overwrite/delete token.
  The bar uses a half-logical-pixel render transform, is separated from the active field highlight,
  and grows with the wider of the numerator or denominator. Left from immediately after a completed
  fraction reopens its denominator; another Left selects its numerator (and then the whole-number
  field for a mixed number). Inside each field, only the character under the forward cursor is
  inverted; it uses the standard half-second blink and is positioned from the scaled glyph metrics
  so repeated or wide digits remain centered. Left/Right, typing, and Delete operate on individual
  elements. Moving Left past the first numerator or whole-number element commits valid edits and
  exits to a narrow cursor before the fraction; an incomplete edit restores the original token.
  Moving Right from that position reopens the first numerator or whole-number field and traverses
  the fraction element by element before exiting after the denominator.
  Recalling a fractional result from Home history restores the same
  structured fraction instead of ordinary `/` division. A Home expression that uses a fraction
  template defaults to a reduced fraction result unless the same expression contains decimal input.
- **F2 FUNC**: absolute value, arbitrary-base logarithm, square/nth root, permutations,
  combinations, and factorial are available. Numerical derivative, numerical integral, and
  summation remain visible but unavailable.
- **F3 MTRX**: quick MathPrint matrix templates with selectable row and column counts.
- **F4 YVAR**: function variables such as `Y1`–`Y9` and `Y0`.
- **F5 SPECIAL**: context-sensitive commands for an interactive graph, drawing operation, or program
  editor. It has no global fixed contents and does nothing where no special menu is available.

Alpha X,T,θ,n remains deferred as a separate direct fraction-template shortcut.

### Letters and symbols

| Physical keys | Alpha result |
|---|---|
| Math, Apps, Prgm | `A`, `B`, `C` |
| x⁻¹, sin, cos, tan | `D`, `E`, `F`, `G` |
| ^, x², comma, `(`, `)`, ÷, log | `H` through `N` |
| 7, 8, 9, ×, ln | `O` through `S` |
| 4, 5, 6, − | `T` through `W` |
| sto→, 1, 2 | `X`, `Y`, `Z` |
| 3 | `θ` |
| + | quote `"` |
| 0 | space |
| . | colon `:` |
| (−) | question mark `?` |
| Enter | SOLVE in the Numeric Solver only |

Alpha A-G already act as physical Zoom shortcuts while the Zoom view is active. Outside Zoom, all
letters and `θ` insert their persistent scalar variable. Quote, space, colon, and question mark are
deferred with strings/programming. SOLVE is deferred with the Numeric Solver screen.

| View | Alpha A–Z/θ behavior |
|---|---|
| Home | Inserts the variable at the edit cursor using the active insert/overwrite mode. |
| Y= | Inserts the variable into the selected equation. |
| Window | Inserts the variable into the selected setting expression. |
| Mode | Consumes Alpha and performs no setting change. |
| Graph / Trace | Consumes Alpha and leaves graph interaction unchanged. |
| Zoom | Physical Alpha A–G retain their existing Zoom shortcuts; H–Z/θ are consumed with no menu action. |
| Zoom Factors | Consumes Alpha and leaves factor selection unchanged. |

## Keys with no shifted function

When their artwork has no legend, Mode, arrows, Clear, Stat/Vars Alpha mappings, On Alpha, and other
blank cells perform no shifted action. They still consume the active one-shot modifier. This explicit
no-op prevents accidental Normal behavior.
