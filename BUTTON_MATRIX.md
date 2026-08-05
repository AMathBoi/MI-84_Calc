# MI-84 Button Matrix

This is the behavioral contract for the calculator's 50 physical keys. It records both the current
implementation and the intended TI-84-style Normal, 2nd, and Alpha meanings. Detailed behavior and
menu contents live in `BUTTON_REFERENCE.md`; implementation order lives in
`BUTTON_IMPLEMENTATION_PLAN.md`.

An unimplemented shifted command remains an explicit placeholder in code until its own implementation
change. It must consume the active modifier and must never fall through to the Normal command.

## Status vocabulary

- **Complete**: implemented and usable in the relevant existing views.
- **Partial**: implemented in some contexts but not all documented contexts.
- **Not implemented**: planned behavior that can use an existing view or editor.
- **Deferred — visual review**: requires a new menu, screen, prompt, template, indicator, or other
  presentation that must be visually approved before implementation.
- **Deferred — subsystem**: depends on a major domain such as lists, matrices, statistics,
  programming, linking, or applications. It also requires visual review if it creates a new view.
- **No shifted function**: the artwork has no shifted meaning. The modifier is consumed without
  performing the Normal command.

View-specific editors may interpret a typed command differently. For example, a digit edits Home,
Y=, or Window depending on the active view.

| Physical key | Normal command | Normal status | 2nd command | 2nd status | Alpha command | Alpha status |
|---|---|---|---|---|---|---|
| Y= | Open Y= view | Complete | STAT PLOT menu | Partial — configuration plus Scatter/Line graph rendering complete; distribution plot types deferred | F1 FRAC function-menu overlay | Complete — approved Phase 4 overlay and fraction template |
| Window | Open Window view | Complete | TBLSET screen | Complete — approved Phase 6 TABLE SETUP with persistent values and Auto/Ask choices | F2 FUNC function-menu overlay | Partial — approved Phase 4 overlay; numerical calculus rows visibly unavailable |
| Zoom | Open Zoom view | Complete | FORMAT screen | Partial — renderer-backed settings complete; PolarGC visible but deferred with polar graph functions | F3 MTRX shortcut menu | Deferred — subsystem and visual review |
| Trace | Begin trace in Graph | Complete | CALC graph-analysis menu | Deferred — visual review | F4 YVAR shortcut menu | Complete — approved two-column Function Y1–Y9 menu |
| Graph | Open Graph view | Complete | TABLE view | Complete — approved Phase 6 graph table with Auto/Ask values and editable Y headers | F5 context-sensitive special menu | Deferred — visual review |
| 2nd | Toggle one-shot 2nd layer | Complete | Toggle/cancel 2nd | Complete | Switch from Alpha to 2nd | Complete |
| Mode | Open Mode view | Complete | Quit: return to Home | Complete | No shifted function | No shifted function |
| Del | Forward delete | Complete | INS: toggle insert/overwrite editing; create a named list when a STAT→Edit header is selected | Complete — context-sensitive behavior with a visible insert cursor | No shifted function | No shifted function |
| Down | Move/select down | Complete | No shifted function | No shifted function | No shifted function | No shifted function |
| Up | Move/select up | Complete | No shifted function | No shifted function | No shifted function | No shifted function |
| Alpha | Toggle one-shot Alpha layer | Complete | A-LOCK: persistent Alpha lock | Complete | Toggle/cancel Alpha | Complete |
| X,T,θ,n | Insert `X` | Complete | LINK SEND/RECEIVE screen | Deferred — subsystem and visual review | Open direct `n/d` fraction template | Complete — approved Phase 4 structured fraction editor |
| Stat | STAT EDIT table | Complete for Phase 5 real-list editing; CALC/TESTS belong to Phase 9 | LIST NAMES/OPS/MATH menu | Complete for reviewed real-list scope; Select and matrix conversions remain with owning later phases | No shifted function | No shifted function |
| Left | Move/select left | Complete | No shifted function | No shifted function | No shifted function | No shifted function |
| Right | Move/select right | Complete | No shifted function | No shifted function | No shifted function | No shifted function |
| Math | MATH/NUM/CMPLX/PROB/FRAC menu | Partial: approved Phase 4 menu; supported scalar/template rows complete and dependent rows visibly unavailable | TEST/LOGIC/CONDITIONS menu | Partial: TEST/LOGIC complete; CONDITIONS visible but unavailable pending template review | Insert `A`; Zoom shortcut A in Zoom | Complete |
| Apps | Installed-applications menu | Deferred — subsystem and visual review | ANGLE menu | Partial: degree/radian markers and coordinate conversions complete; minute and `►DMS` visibly unavailable | Insert `B`; Zoom shortcut B in Zoom | Complete |
| Prgm | PRGM EXEC/EDIT/NEW menu | Deferred — subsystem and visual review | DRAW/POINTS/STO menu | Deferred — subsystem and visual review | Insert `C`; Zoom shortcut C in Zoom | Complete |
| Vars | VARS/Y-VARS menu | Partial — approved nested Window X/Y, Zoom ZX/ZY, and Y-VARS Function menus; other domain tabs and unsupported variables visibly unavailable | DISTR/DRAW menu | Deferred — subsystem and visual review | No shifted function | No shifted function |
| Clear | Clear active editor/value | Complete | No shifted function | No shifted function | No shifted function | No shifted function |
| x⁻¹ | Apply/insert reciprocal | Complete | MATRIX NAMES/MATH/EDIT menu | Deferred — subsystem and visual review | Insert `D`; Zoom shortcut D in Zoom | Complete |
| sin | Insert `sin(` | Complete | Insert `sin⁻¹(` | Complete | Insert `E`; Zoom shortcut E in Zoom | Complete |
| cos | Insert `cos(` | Complete | Insert `cos⁻¹(` | Complete | Insert `F`; Zoom shortcut F in Zoom | Complete |
| tan | Insert `tan(` | Complete | Insert `tan⁻¹(` | Complete | Insert `G`; Zoom shortcut G in Zoom | Complete |
| ^ | Insert exponent operator | Complete | Insert `π` | Complete | Insert `H` | Complete |
| x² | Apply/insert square | Complete | Insert linear `sqrt(` | Complete | Insert `I` | Complete |
| , | Insert comma | Partial: evaluates implemented multi-argument scalar functions; broader function families remain deferred | Insert `EE` exponent marker | Complete — exponent limited to `-308..308` with explicit range errors | Insert `J` | Complete |
| ( | Insert opening parenthesis | Complete | Insert `{` list opener | Complete — Home and supported expression editors plus list-header editing | Insert `K` | Complete |
| ) | Insert closing parenthesis | Complete | Insert `}` list closer | Complete — Home and supported expression editors plus list-header editing | Insert `L` | Complete |
| ÷ | Insert division operator | Complete | Insert Euler's constant `e` | Complete | Insert `M` | Complete |
| log | Insert `log(` | Complete | Insert `10^(` | Complete | Insert `N` | Complete |
| 7 | Insert digit / Zoom hotkey 7 | Complete | Insert sequence variable `u` | Deferred — subsystem | Insert `O` | Complete |
| 8 | Insert digit / Zoom hotkey 8 | Complete | Insert sequence variable `v` | Deferred — subsystem | Insert `P` | Complete |
| 9 | Insert digit / Zoom hotkey 9 | Complete | Insert sequence variable `w` | Deferred — subsystem | Insert `Q` | Complete |
| × | Insert multiplication operator | Complete | Insert `[` matrix opener | Deferred — subsystem | Insert `R` | Complete |
| ln | Insert `ln(` | Complete | Insert `e^(` | Complete | Insert `S` | Complete |
| 4 | Insert digit / menu hotkey 4 | Complete | Insert list name `L4` | Complete | Insert `T` | Complete |
| 5 | Insert digit / Zoom hotkey 5 | Complete | Insert list name `L5` | Complete | Insert `U` | Complete |
| 6 | Insert digit / Zoom hotkey 6 | Complete | Insert list name `L6` | Complete | Insert `V` | Complete |
| − | Insert subtraction operator | Complete | Insert `]` matrix closer | Deferred — subsystem | Insert `W` | Complete |
| sto→ | Insert store operator | Complete for scalar `expression→variable` | RCL variable-selection prompt | Deferred — visual review | Insert `X` | Complete |
| 1 | Insert digit / menu hotkey 1 | Complete | Insert list name `L1` | Complete | Insert `Y` | Complete |
| 2 | Insert digit / menu hotkey 2 | Complete | Insert list name `L2` | Complete | Insert `Z` | Complete |
| 3 | Insert digit / menu hotkey 3 | Complete | Insert list name `L3` | Complete | Insert `θ` | Complete |
| + | Insert addition operator | Complete | MEM menu | Deferred — visual review | Insert quote `"` | Deferred — subsystem |
| On | Wake/cancel calculator operation | Deferred — visual review | OFF: turn LCD off while retaining memory | Deferred — visual review | No shifted function | No shifted function |
| 0 | Insert digit / Zoom hotkey 0 | Complete | No shifted function | No shifted function | Insert space | Deferred — subsystem |
| . | Insert decimal point | Complete | Insert imaginary unit `i` | Complete | Insert colon `:` | Deferred — subsystem |
| (−) | Toggle current operand sign | Complete in Home, Y=, and Window | Insert `Ans` | Complete | Insert question mark `?` | Deferred — subsystem |
| Enter | Submit/confirm/select | Complete | ENTRY: recall earlier Home entries | Complete | SOLVE in Numeric Solver | Deferred — visual review |

## View interpretation summary

| View | Accepted primary command groups |
|---|---|
| Home | Numeric editing, functions, operators, history navigation, submit |
| Y= | Expression editing and equation selection |
| Window | Numeric expression editing and setting selection |
| TABLE SETUP | Numeric expression editing plus Auto/Ask selection |
| TABLE | Auto/Ask X and Y cells, scrolling, and Y-header editing |
| FORMAT | Persistent graph-format selection |
| Mode | Four-way option/category navigation |
| Zoom | Tabs, menu movement, physical numeric shortcuts, and physical Alpha A-G shortcuts |
| Zoom Factors | Factor movement, selection, and physical numeric shortcuts 1-6 |
| STAT menu / list editor | Tab navigation, real-list editing, header literals, and named-list creation |
| STAT PLOT | Main actions, Plot1–Plot3 tabs, and persistent plot settings |
| Compact token menu | Tabs, row navigation, physical numeric and Alpha A-D hotkeys where displayed, Enter selection, Clear cancellation, and direct-view exits |
| Graph | Trace movement or interactive zoom cursor movement |

## Placeholder policy

- Replace one placeholder at a time with a typed command and regression tests.
- Record the intended behavior in this matrix before implementation.
- Define behavior in every affected view, including whether the key is ignored.
- Preserve one-shot modifier consumption even when the selected feature remains deferred.
- Do not start a deferred visual feature until its menu or screen has been separately reviewed.
- Never implement shifted behavior by checking a displayed legend string.
