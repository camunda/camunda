## Scan: 2026-07-28 — filter select tooltip: left-align instead of center (flag-on only)

Same-day follow-up: filter Select's tooltip was centered under the trigger
(`left-1/2 -translate-x-1/2`); changed to `left-0` (dropped the translate)
so its left edge lines up with the trigger's left edge instead. CSS-only,
inside the already flag-gated `FilterSelectDS` block. `tsc --noEmit` clean.
Live-verified: dispatched hover, tooltip's `getBoundingClientRect().left`
now matches the trigger button's `left` exactly (both `72`). 3001 unaffected
by construction (this code path doesn't exist there at all).

## Scan: 2026-07-28 — five fixes: tooltip wrap, icon/text gap, tag gap, empty state, filter tooltip (flag-on only)

1. **Sort tooltip wrapped to two lines** — root cause was Tailwind's
   `text-balance` utility (`text-wrap: balance`), which can force a short
   two-word string onto two visually "balanced" lines even when it fits on
   one. Removed it, added `whitespace-nowrap` to guarantee a single line
   regardless of container width.
2. **2px more gap between tile icon and text, centered** —
   `LabelWithPopover.module.scss`'s `.labelDS` (the shared trigger span both
   `PriorityLabel` and `DateLabel` render into) gained `display: inline-flex;
   align-items: center; gap: 0.25rem`. Carbon's `vertical-align:
   text-bottom` was an approximation, not a true center — now genuinely
   centered. Added a new `.inlineIconDS` (in both `Task.module.scss` and
   `PriorityLabel.module.scss`, wired via `cn()`) that zeroes each icon's
   own Carbon-era `margin-right` so the old margin and the new `gap` don't
   stack into 6px.
3. **4px gap between candidate tags** — `Aside.tsx`'s candidate `Tag`s had
   no wrapping container at all before, so no gap was possible. Added a
   DS-only `<div className="flex flex-wrap gap-1">` wrapper around them;
   Legacy keeps the exact original unwrapped sibling markup (session rule:
   never add so much as an extra wrapper `<div>` to old-UI, even a
   functionally invisible one).
4. **Empty state ("No tasks found") → DS `EmptyState`, size="sm", no
   button** — `NoTasks.tsx` now early-returns `<EmptyState size="sm"
   icon={<Search aria-hidden />} heading={...} description={...} />` for
   DS, reusing the exact same two translation keys as before. Legacy keeps
   its original hand-rolled `Stack`/icon/heading/body markup untouched.
   Removed the now-dead `.iconDS`/`.headingDS`/`.bodyDS` rules from
   `NoTasks.module.scss` (the DS path no longer renders any of those spans
   at all).
5. **Tooltip below the filter Select reading "Task filters"** — same
   hand-rolled `<div role="tooltip">` pattern as the sort button's (proven
   reliable; Radix Tooltip's controlled mode is not, per the previous
   round's investigation). Added local `isFilterSelectOpen`/
   `isFilterTooltipOpen` state to `FilterSelectDS`, wired the same way:
   hover shows it, opening the select or clicking hides it, focus-return
   after closing has no path to reopen it. New `taskFiltersSelectButton`
   i18n key ("Task filters") added to all 4 locales — de/fr/es are my own
   translations, unreviewed.

`tsc --noEmit`/`eslint` clean, all 4 locale JSON files re-validated as
parseable. Live-verified all five on 3000 (via a mix of screenshots and
dispatched `MouseEvent`s + computed-style checks, since the browser tool's
synthetic `hover` remains unreliable for Radix-adjacent components):
tooltip computed `white-space: nowrap`; priority/date label computed
`display: flex; align-items: center; gap: 4px` with the icon's own
`margin-right: 0px`; candidate-tag wrapper computed `display: flex; gap:
4px; flex-wrap: wrap` over 4 real tags; `EmptyState` computed class list
includes `py-8` (confirms `size="sm"`) and has zero `button`/`a` descendants;
hovering the filter Select renders a `role="tooltip"` div with text
"Task filters". 3001 (flag-off) — screenshot confirms every one of the five
areas (icon, tags, empty state, both tooltips) is completely unchanged.

## Scan: 2026-07-28 — sort tooltip: abandoned Radix Tooltip, hand-rolled instead (flag-on only)

Real bug report: "hover to show tooltip, then click to reveal the menu,
then click outside to close the menu or select an option — I see the
tooltip still." Reproduced live exactly as described.

**Root cause**: Radix `DropdownMenu` returns focus to its trigger when it
closes (correct a11y — keeps keyboard navigation sane), and Radix
`Tooltip` opens on focus as well as hover (also correct a11y — keyboard
users get tooltips too). Combined, closing the sort menu — by selecting an
option *or* clicking outside — reprograms focus onto the trigger button,
which Tooltip's own focus-listener interpreted as "show the tooltip,"
regardless of whether the mouse was still hovering it.

**First fix attempt (controlled `open` state) hit a wall**: tried
`open={isSortTooltipOpen && !isSortMenuOpen}` on `Tooltip`, driven purely by
real `onMouseEnter`/`onMouseLeave` on the button (bypassing Radix's own
focus-triggering entirely). Confirmed via `window.__debugEnter` counter
instrumentation that the handler fires correctly and state updates — but
`Tooltip.Root`'s `data-state` never left `"closed"`, tested across **all
four combinations** of nesting order (`DropdownMenuTrigger`/`TooltipTrigger`
each first) × `onOpenChange` presence (bare `open` alone vs. an
`onOpenChange` that only accepts `false`). Controlled `open` on this
Tooltip primitive simply doesn't render in this environment, independent of
the nesting-order bug fixed in the previous round.

**Final fix**: dropped the DS `Tooltip`/`TooltipTrigger`/`TooltipContent`/
`TooltipProvider` primitives entirely for this button. Replaced with a
plain conditionally-rendered `<div role="tooltip">`, absolutely positioned
below the trigger (`absolute top-full ... -translate-x-1/2`), reusing
`camunda-design-system/src/components/ui/tooltip.tsx`'s own Tailwind classes
verbatim so it's visually identical to a real DS tooltip. Driven only by
`onMouseEnter`/`onMouseLeave`/`onClick` state — no Radix Tooltip involved at
all, so there's no focus-triggering path to reintroduce the bug. Only one
`asChild` remains (`DropdownMenuTrigger` on the `Button`), eliminating the
double-`asChild` fragility from the previous round too.

`tsc --noEmit`/`eslint` clean. Live-verified the exact reported sequence on
3000: hover → tooltip appears below the button; click → tooltip disappears,
menu opens; select "Priority" → menu closes, list re-sorts, **tooltip stays
hidden** even with the mouse still resting on the button (previously it
would reappear here); reopened and closed via click-outside → same result,
tooltip stays hidden. 3001 (flag-off) — confirmed the 6 `role="tooltip"`
elements present there are Legacy's own pre-existing Carbon tooltips
(Open Info/Settings/Filter/Close/Sort tasks etc.), unrelated to and
untouched by this change.

## Scan: 2026-07-28 — sort tooltip: side=bottom + fixed a real double-asChild bug (flag-on only)

Follow-up to the sort-button tooltip: asked for `side="bottom"` (the
top-side default was getting clipped/hidden above the trigger, which sits
near the top of the panel). Adding just the `side` prop surfaced a
pre-existing, real bug in how the Tooltip and DropdownMenu triggers were
nested — not something the `side` change itself caused, but the
investigation to verify `side="bottom"` actually worked is what caught it.

- **Bug found**: the original nesting was `TooltipTrigger asChild >
  DropdownMenuTrigger asChild > Button`. Live DOM inspection showed the
  merged button's `data-slot` was `"tooltip-trigger"` only and its
  `aria-controls` was missing — `DropdownMenuTrigger`'s own attributes were
  getting silently dropped by the outer `Slot`. Nesting two different Radix
  primitives' `asChild` directly on each other is fragile; it happened to
  render tooltip content once during earlier testing, but wasn't reliable.
- **First fix attempt was also wrong**: moved `DropdownMenuTrigger` outside
  but put `TooltipProvider`/`Tooltip` *inside* that `asChild` chain.
  Radix's `Slot` clones props onto its one child element — `TooltipProvider`/
  `Tooltip` are context-only components that render no DOM element of their
  own, so `Slot` had nothing real to clone onto, breaking composition
  entirely (worse than the original).
- **Correct fix**: `TooltipProvider`/`Tooltip` wrap the whole thing from the
  *outside* (context-only, fine to wrap anything); the actual `asChild`
  chain is directly `DropdownMenuTrigger asChild > TooltipTrigger asChild >
  Button` with nothing non-forwarding in between. Dropdown wins the
  outermost `asChild` slot since its click/open wiring is functionally
  critical; Tooltip (cosmetic, hover-only) is the inner, more-forgiving one.
- `tsc --noEmit`/`eslint` clean. Live-verified (via dispatched `PointerEvent`s
  + polling, since the browser tool's synthetic `hover` doesn't reliably
  fire Radix's listeners): `data-slot` on the trigger is correctly
  `"dropdown-menu-trigger"`; clicking still opens the sort menu right-aligned
  as before; hovering opens the tooltip with `data-side="bottom"`, positioned
  below the button (`top: 92` vs. the button's own `bottom: 88` — no overlap,
  not clipped off-screen). 3001 (flag-off) — confirmed zero
  `dropdown-menu-trigger`/`tooltip-content` slots exist in that DOM at all.

## Scan: 2026-07-28 — sort button tooltip (flag-on only)

Prompted by the Product Builder selecting the sort trigger button and
asking for a hover tooltip reading "Sort tasks" (it already had a native
`title` attribute, but no real DS Tooltip like the rest of the DS's
icon-only buttons — e.g. AppSidebar's collapsed rail).

- `Filters.tsx`'s DS sort-menu bypass (built directly on
  `DropdownMenu`/`DropdownMenuTrigger`/`DropdownMenuContent` after the
  earlier alignment-gap fix) now wraps its trigger button in
  `Tooltip`/`TooltipTrigger`/`TooltipContent` from `@camunda/design-system`,
  with a local `TooltipProvider` (no app-root provider exists yet — same
  stopgap precedent as `CollapsiblePanel.tsx`/`LabelWithPopover.tsx`).
  Nesting: `TooltipTrigger asChild` wraps `DropdownMenuTrigger asChild`
  wraps the `Button` — both Radix `Slot`s merge their props onto the same
  underlying `<button>`.
- Tooltip text reuses the existing `tasklist.taskFiltersSortButton`
  translation key (same one already used for the button's `aria-label`/
  `title`) — no new i18n keys needed.
- `tsc --noEmit`/`eslint` clean. Live-verified via direct DOM inspection
  (the browser tool's synthetic `hover` action doesn't reliably fire the
  `pointerenter` sequence Radix's Tooltip listens for, so screenshots alone
  weren't conclusive — dispatched real `PointerEvent`s instead): the
  tooltip renders with the exact text "Sort tasks", positioned correctly
  relative to the trigger, full opacity, `data-state` progressing through
  `delayed-open`, and disappearing again after the pointer "leaves" —
  confirms the hover-open/close lifecycle works. Change is entirely inside
  the existing `featureFlags.dsTasklistUI` branch (Legacy's `OverflowMenu`
  path never references the new Tooltip imports), so 3001 can't be
  affected by construction — confirmed no `Sort tasks`-labeled button
  picked up any new attributes there.

## Scan: 2026-07-28 — reverted border token back to --border (flag-on only)

Prompted by: "but now the borders are way too dark. the borders should be
--border token. Why did that change?" Direct reversal of the prior round's
solid-token swap — the Product Builder had chosen the solid
`--neutral-border-strong` option when asked, but decided against it once
seeing it live (darker/more prominent than wanted).

Reverted `var(--neutral-border-strong)` → `var(--border)` in the same 6
files that round touched: `Task.module.scss`,
`AutoSelectNextTaskToggle.module.scss`, `Filters.module.scss`,
`taskDetailsLayoutCommon.module.scss`, `TasksLayoutPage.module.scss`,
`TaskDetailsTaskPage.module.scss`. Pure token-name revert, structure
untouched — importantly this **keeps** the separate `border-top` shorthand
fix from the bug report right before this (Carbon's `border-top: none`
zeroing border-style, not just color): that fix only needed the full
shorthand rather than a color-only override, independent of which token
supplies the color, so reverting the token didn't reintroduce that bug.

`tsc --noEmit` clean. Live-verified: 3000 — card border computed color is
back to the translucent `color(srgb 0.622... / 0.2)` value (matches
`--border` exactly, confirmed identical to before the solid-token
experiment); the tile-after-active still shows `border-top-style: solid`
(the earlier shorthand fix holds independently of token choice). 3001
(flag-off) — unchanged.

Net effect of the last two rounds: back to the visually-softer `--border`
look that originally prompted the "is that a drop shadow?" question — that
softness is an inherent property of `--border`'s translucency, not a bug;
flagged here in case it comes up again.

## Scan: 2026-07-28 — fixed: tile below the active one lost its top border (flag-on only)

Follow-up bug report after the solid-border-token fix: "the tile below
shows the top border being removed" whenever a task tile is selected. Real
bug, not a rendering artifact this time.

- **Root cause**: `Task.module.scss`'s Carbon-legacy `&.active + &
  .taskLink:not(:focus) { border-top: none; ... }` rule uses the `border-top`
  *shorthand*, which zeroes `border-top-style` (not just color) for the
  tile immediately following an active one — a leftover from the old
  stacked-row design where suppressing that divider was intentional
  (avoiding a double-line where the active tile's own bottom edge met the
  next tile's top edge). My earlier override for this rule (from the
  "borders back" round) only reset `border-color`, never `border-style` —
  so even after restoring the border, Carbon's `none` *style* still won on
  specificity for that one sub-property, leaving the border invisible
  regardless of what color was set. Confirmed directly: computed
  `border-top-style` on the tile after an active one was `none` before this
  fix.
- **Fix**: changed the override in `.containerDS` from `border-color: ...`
  to the full `border-top: 1px solid var(--neutral-border-strong)` shorthand,
  resetting style/width/color together so none of Carbon's sub-properties
  leak through.
- No `.tsx` changes (CSS-only). Live-verified: 3000 — selected the first
  tile, confirmed the next tile's computed `border-top-style` is now
  `solid` (was `none`), `border-top-color` matches the same
  `--neutral-border-strong` value used everywhere else, screenshot shows a
  continuous, unbroken border around every tile regardless of adjacency to
  the active one. 3001 (flag-off) — untouched; the identical Carbon rule
  there is intentional in the old stacked-row design (not a bug for that
  UI), so no live test needed for that side, just confirmed via DOM that
  nothing there was touched.

## Scan: 2026-07-28 — border token: translucent --border → solid --neutral-border-strong (flag-on only)

Prompted by: "why is there a drop shadow on the selected [card]? I don't
think there should be one" / "it seems to hide the tile below's top border."
Investigated thoroughly before touching anything — `box-shadow`, `filter`,
`mask-image`, and `::before`/`::after` all computed to `none` on the card,
its `Stack` wrapper, and the list container. Root cause wasn't a shadow at
all: `--border` resolves to a **translucent** color
(`color-mix(in srgb, var(--color-zinc-400) 20%, transparent)`, confirmed in
`camunda-design-system/src/index.css`), not a solid gray. At a rounded
corner, that translucent border anti-aliases against the card's fill and
the gutter's `--background` behind it — three close-in-lightness tones
blending at a curve — reading as a soft blur rather than a crisp line, and
making the next tile's top border nearly disappear into it. Confirmed the
DS ships a solid alternative for exactly this: `--neutral-border-strong`
(zinc-300, fully opaque, no color-mix).

Asked the Product Builder whether to fix by switching to the solid token or
by dropping the border entirely (back to gap-only separation, like a few
rounds ago) — they chose the solid, higher-contrast token. Since earlier
rounds today deliberately unified every divider in this area onto the same
`--border` value for consistency ("so they all use the same border token
color"), swapped the token everywhere that prior work touched, not just the
task tile, to keep that same-token invariant intact:
`Task.module.scss` (card border, all states),
`AutoSelectNextTaskToggle.module.scss`, `Filters.module.scss` (panel header
divider), `taskDetailsLayoutCommon.module.scss` (`.aside`'s left divider —
its `.headerBorder`/tabs-divider/`ContainedList` dividers were already
removed to `none`/`transparent` in a separate earlier round, untouched here),
`TasksLayoutPage.module.scss` (list/details panel divider),
`TaskDetailsTaskPage.module.scss` (footer divider above Complete Task).
Pure find-and-replace of `var(--border)` → `var(--neutral-border-strong)`
in each file — no new selectors, no wiring changes.

`tsc --noEmit` clean (CSS-only change, no `.tsx` touched). Live-verified:
3000 — card border computed color is now a fully opaque `oklch(...)` (no
alpha channel, confirming solid vs. the old translucent `color-mix` value);
screenshot shows crisp, clearly visible borders around every card with no
blur at the corners. 3001 (flag-off) — screenshot confirms no change.

## Scan: 2026-07-28 — tile title size, borders back, 8px gap, priority icons (flag-on only)

Four separate requests in one round, all flag-on only:

1. **Tile title too large** — `Task.module.scss`'s `.nameDS` was Tailwind
   `text-base` (1rem/16px), read disproportionate next to `.labelDS`'s
   `text-xs` (12px) row below it. Sized down to `text-sm` (0.875rem/20px
   line-height), weight/color unchanged.
2. **Borders back on cards** — reverts the border-removal round from
   earlier today. `.containerDS`'s base `.taskLink` rule gets
   `border: 1px solid var(--border)` back, plus the full set of
   specificity-matched overrides needed to beat the Carbon-legacy
   `:first-child`/`&.active + &`/`:not(.active)`/`:not(.active):last-child`
   border rules (same reasoning documented in the original border-redesign
   entry — re-added verbatim since removing them was only safe while the
   border was `none`).
3. **Card gap 4px → 8px** — `AvailableTasks.module.scss`'s
   `.listContainerDS` gap changed from `0.25rem` to `0.5rem`.
4. **Priority icons** — `#/shared/design-system-compat/index.ts`'s
   `SkillLevelBasicIcon`/`SkillLevelIntermediateIcon`/`SkillLevelAdvancedIcon`
   (low/medium/high) changed from lucide `SignalLow`/`SignalMedium`/
   `SignalHigh` to `ChevronsDown`/`Equal`/`ChevronsUp` respectively, per
   explicit request. `CriticalIcon` (`TriangleAlert`) untouched — not
   mentioned. Removed the now-unused `SignalLow`/`SignalMedium`/`SignalHigh`
   lucide imports (no other consumer). Only one file
   (`PriorityLabel.tsx`) consumes these compat exports, confirmed via grep
   before editing the shared compat layer directly.

`tsc --noEmit` clean. `eslint` on `design-system-compat/index.ts` reports
72 pre-existing `import-x/group-exports` errors — confirmed via `git stash`
that the exact same 72 errors exist on the pre-edit version of the file, so
this is unrelated lint debt, not something introduced here.

Live-verified: 3000 — title computed to 14px/20px; card border computed to
the same `--border` color used throughout, radius still 12px; list gap
computed to 8px; "Medium" priority badges render `lucide-equal`; the
active-card and its preceding sibling both still show fully consistent
border colors (the specificity fix still holds after re-adding the border).
3001 (flag-off) — screenshot confirms Carbon's original bar-chart priority
icon, stacked-row borders, and no gap are all unchanged.

## Scan: 2026-07-28 — sort menu alignment fix + icon swap (flag-on only)

Prompted by: "why is the menu center aligned. make the menu alignment like
the default. make the component the default again. only swap the icon to
arrow-down-narrow-wide and make the select menu to the original." The
Product Builder's selected element revealed a genuine compat-layer gap, not
something introduced this session — the sort dropdown had always been
center-aligned under its trigger for the DS build.

- **Root cause**: `Filters.tsx` renders the sort control through
  `#/shared/design-system-compat`'s `OverflowMenu`, which resolves to
  `@camunda/design-system/carbon-compat`'s `OverflowMenu` adapter when the
  flag is on. Read that adapter's source directly
  (`carbon-compat/overflow-menu.tsx`) — it calls `warnDroppedProps` on
  `align`, `menuOptionsClass`, `direction`, `flipped`, and others, and its
  `DropdownMenuContent` is rendered with no `align` prop at all. Radix's own
  default for `DropdownMenuContent` is `align="center"`, so the DS render
  always centered the menu under the icon-only trigger regardless of the
  `align="bottom"` Filters.tsx was passing (a Carbon-only prop name/value
  that was never going to map to Radix's `align` anyway) — a real bug, not
  a regression from this session's work.
- **Fix**: bypassed the compat layer for this one control — same pattern as
  `ProcessesSelect.tsx`'s `avoidCollisions`/`side` gap earlier this session.
  Built the DS branch directly from `@camunda/design-system`'s own
  `DropdownMenu`/`DropdownMenuTrigger`/`DropdownMenuContent`/
  `DropdownMenuItem` primitives (the same components the compat adapter
  wraps), passing `align="end"` explicitly. Trigger markup mirrors the
  compat adapter's own structure exactly (`Button variant="ghost"
  size="icon"` wrapped in `DropdownMenuTrigger asChild`) — "the default
  again," just with the alignment bug actually fixed.
- **Icon swap**: `SortAscendingIcon` (Carbon/compat, `ArrowUpNarrowWide`) →
  lucide's `ArrowDownNarrowWide` directly, DS-only. Legacy keeps
  `SortAscendingIcon` unchanged.
- **Checkmark**: swapped `CheckmarkIcon` (Carbon compat export) for lucide's
  own `Check` in the DS branch, matching the DS Select's own checkmark
  convention rather than a Carbon icon leaking into DS markup.
- `Filters.tsx`'s sort control is now a full DS/Legacy split (inline
  ternary, no separate component needed — no new hooks introduced, so no
  rules-of-hooks concern): DS renders the direct-primitive dropdown above,
  Legacy renders the original `OverflowMenu`/`OverflowMenuItem` completely
  unchanged.
- `tsc --noEmit`/`eslint` clean. Live-verified: 3000 — opening the sort menu
  now right-aligns flush under the trigger (`data-align="end"`, confirmed via
  DOM), icon is `lucide-arrow-down-narrow-wide`, checkmark still correctly
  marks the active sort option. 3001 (flag-off) — screenshot confirms the
  original Carbon ascending-sort icon and menu are untouched.

## Scan: 2026-07-28 — EmptyState down to a single button (flag-on only)

Same-day follow-up: "put view tutorial in the main button and remove the
ghost button." Collapsed the two-CTA layout (primary "Learn how to create
tasks" + secondary link "View tutorial") down to one.

- `NoTaskSelectedPage.tsx` — removed the `secondaryAction` prop entirely;
  `action` is now the only button.
- Repurposed the key rather than leaving two: `taskEmptyTutorialCta`'s value
  changed to "View tutorial" (was "Learn how to create tasks") in all 4
  locales; the now-unused `taskEmptyTutorialSecondaryCta` key deleted from
  all 4. One button, one key, no dead translation entries.
- `tsc --noEmit`/`eslint` clean, all 4 locale JSON files re-validated.
  Live-verified: 3000 shows exactly one button labeled "View tutorial"
  linking to the tutorial URL; no secondary link present.

## Scan: 2026-07-28 — "Welcome to Tasklist" → DS EmptyState (flag-on only)

Prompted by the Product Builder selecting the first-time-user "Welcome to
Tasklist" panel and asking to rebuild it with the DS's `EmptyState`
component (`src/components/ui/empty-state.tsx`), with a `Check` (lucide)
icon, a primary button "Learn how to create tasks," and a secondary
"View tutorial" link below it.

- **`NoTaskSelectedPage.tsx`** — added an early-return branch, only taken
  when `featureFlags.dsTasklistUI` is on AND `!isOldUser` (the first-time
  "Welcome to Tasklist" state specifically — the `isOldUser` "pick a task"
  prompt keeps its existing Grid/Column markup unchanged for both UIs,
  out of scope for this request). Renders DS `EmptyState` directly
  (bypasses the compat layer — direct `@camunda/design-system` import, safe
  here since it's a plain early-return with no extra hooks, not a case
  needing a full DS/Legacy component split):
  - `icon`: `<Check aria-hidden />` (lucide-react), per the Product
    Builder's explicit choice — the Legacy path keeps its own
    `SvgOrangeCheckMark`, untouched.
  - `heading`/`description`: reuses the existing `taskEmptyHeader`/
    `taskEmptyDetail1`/`taskEmptyDetail2`/`taskEmptyTaskAvailablePrompt`
    translation keys, same content as before.
  - `action`: DS `Button` with `asChild`, wrapping a real `<a>` to the
    tutorial URL, labeled via a new `taskEmptyTutorialCta` key ("Learn how
    to create tasks").
  - `secondaryAction`: DS `Button variant="link" size="sm"` with `asChild`,
    same URL, labeled via a new `taskEmptyTutorialSecondaryCta` key ("View
    tutorial") — per the EmptyState stories' own `WithSecondaryAction`/
    `InsideCard` precedent (`action` + `secondaryAction` stack vertically
    inside the component itself, no manual layout needed).
- **New i18n keys** added to all 4 locale files (`en`/`de`/`fr`/`es`) —
  `taskEmptyTutorialCta`, `taskEmptyTutorialSecondaryCta`. The
  de/fr/es values are my own direct translations (not reviewed by a native
  speaker) — flagging for a translator review pass before this ships
  broadly, same caveat as any new non-English copy added outside a
  proper localization workflow.
- **`NoTaskSelectedPage.module.scss`** — new `.containerDS` (flex, centers
  the EmptyState in the full details panel). Removed the now-dead
  `.newUserTextDS` rule — the DS first-time-user state no longer renders
  `.newUserText` at all, so there was nothing left to gate; `.newUserText`
  itself is now Old-UI-only (noted in a comment). Cleaned up the
  now-unreachable `styles.newUserTextDS` reference in the `.tsx`'s
  className ternary at the same time (that branch could never actually be
  hit — reaching that shared Grid/Column now requires either the flag being
  off, or `isOldUser` true, never `!isOldUser` with the flag on).
- `tsc --noEmit`/`eslint` clean, all 4 locale JSON files re-validated as
  parseable. Live-verified: 3000 (flag-on) — screenshot matches the
  requested design exactly (circular icon badge with a check mark,
  heading, description, black primary button, blue secondary link below
  it); both anchors resolve to the correct tutorial URL with
  `target="_blank"`. 3001 (flag-off) — screenshot confirms the original
  orange checkmark SVG and inline "Follow our tutorial to learn how to
  create tasks." link are unchanged.

## Scan: 2026-07-28 — filter title becomes a ghost Select (flag-on only)

Prompted by: "this should be facing down and change this to a ghost select
button that shows the filter options shown in the side panel as switchable
in the select options" (selecting the "All open tasks" `<h1>` and the sort
button). Clarified scope with the Product Builder before building (3
questions): (1) target is the `<h1>` title itself, not the sort button; (2)
the sidebar filter list stays exactly as-is — this is an additional, faster
way to switch filters, same underlying URL search state, not a replacement;
(3) the sort control stays a separate, untouched button.

- **`Filters.tsx`** — new `FilterSelectDS` component (DS-only, mounted only
  when `featureFlags.dsTasklistUI` is on) replaces the plain `<h1>` with a
  DS `Select` styled as a ghost button (`border-none bg-transparent`,
  `hover:bg-neutral-background-medium`) — the DS `SelectTrigger` already
  ships a `ChevronDownIcon`, so "facing down" came for free once the
  control became a real Select instead of static text. Options: the 4
  built-in filters (`FILTER_VALUES`) plus any custom filters
  (`getStateLocally('tasklist.customFilters')`), each navigating via the
  same `navigate({to: '.', search: ...})` / `getCustomFilterSearch()` calls
  `CollapsiblePanel.tsx` already uses for its sidebar items — same
  underlying state, so the sidebar and this Select always agree.
  - **Deliberately does NOT include "New filter" creation** — that action
    opens `CustomFiltersModal`, whose open/close state lives in
    `CollapsiblePanel.tsx`, a sibling component. Lifting that state up just
    to duplicate one creation entry point wasn't worth the coupling; the
    sidebar keeps sole ownership of creating new filters. Confirmed
    acceptable via the scoping questions above (Q2: "additional way to
    switch," not "replace/duplicate everything").
  - Split into its own component (not an inline conditional inside
    `Filters`) so its DS-only hooks (`useSuspenseQuery` for the username,
    `useSearch`/`useNavigate`) aren't called conditionally inside a single
    component body — same DS/Legacy-split precedent as `ProcessesSelect.tsx`
    and `LabelWithPopover.tsx`, required by rules-of-hooks even though the
    flag itself never changes at runtime.
- **`Filters.module.scss`** — removed the now-dead `.headerDS` rule (the
  `<h1>` it targeted no longer renders in DS mode at all; its typography is
  now owned entirely by the DS Select's own Tailwind classes). `.header` is
  now Old-UI-only, noted in a comment.
- `tsc --noEmit`/`eslint` clean. Live-verified on 3000: clicking the new
  select opens a dropdown listing "All open tasks / Assigned to me /
  Unassigned / Completed" with a checkmark on the active one; selecting
  "Unassigned" updates the URL to `?filter=unassigned`, the task list, and
  the sidebar's active state in sync (confirmed via `read_page` + URL
  check). 3001 (flag-off) — screenshot confirms the plain `<h1>` "All open
  tasks" title is unchanged, no dropdown/chevron present.

## Scan: 2026-07-28 — remove task-details top/bottom dividers (flag-on only)

Prompted by the Product Builder selecting the `Aside` "Candidates"/"Priority"
row dividers and the `TaskDetailsHeader` bottom border and asking to remove
the top/bottom borders. Continues the same direction as the task-card
redesign — dividers replaced by spacing/color contrast, not lines.

- `taskDetailsLayoutCommon.module.scss`'s `.headerBorder` — removed
  `border-bottom: 1px solid var(--border)`, kept `padding-bottom` for the
  spacing.
- `.content > [data-slot='tabs']` (the tab row divider under the header) —
  removed `border-bottom: 1px solid var(--border)`, kept the padding.
- `.aside`'s `ContainedList` row dividers (`::before` pseudo-element,
  SHIM-tier Carbon component per the earlier finding) — `background-color`
  changed from `var(--border)` to `transparent`.
- All three selectors were already DS-only by construction (`.headerBorder`
  only applied via the flag in `TaskDetailsHeader.tsx`, `[data-slot='tabs']`
  only exists on the DS Tabs component, `.asideDS` only applied via the
  flag in `Aside.tsx`) — no new `.tsx` wiring needed this round.
- CSS-only. Live-verified: 3000 — `headerBorder`'s `border-bottom-width`,
  the tabs wrapper's `border-bottom-width`, and the `ContainedList` item's
  `::before` `background-color` are all `0px`/transparent; screenshot
  confirms no divider line under the header, under the tabs, or between any
  "Details" rows. 3001 — same DS-only selectors don't exist in the old-UI
  DOM at all (confirmed by construction, not just by testing), so nothing
  there was touched.

## Scan: 2026-07-28 — card gap 2px → 4px (flag-on only)

`AvailableTasks.module.scss`'s `.listContainerDS` gap changed from
`0.125rem` (2px) to `0.25rem` (4px). No other change. Live-verified: 3000
computed `gap` is now `4px`.

## Scan: 2026-07-28 — unselected card surface color (flag-on only)

Follow-up to the border-removal round: unselected cards had no
`background-color` of their own at all (`.taskLink`'s base rule only set
radius/padding/border:none), so they were fully transparent and blended
into the list's own `--background` gutter — same color as the gaps between
cards. Added `&:not(.active) .taskLink { background-color: var(--popover);
}` to `Task.module.scss`'s `.containerDS` (between the base shape rule and
the existing hover-background rule) so unselected cards get the DS's card
surface token, distinct from the page background — reads as a raised card
rather than blending in. Hover/active backgrounds unchanged (hover still
`--neutral-background-medium`, active still `--neutral-background-strong`
via `.activeDS`).

No `.tsx` changes (CSS-only). Live-verified: 3000 — unselected card
`background-color` is `rgb(255, 255, 255)`, matching `--popover`'s light-mode
value (`#ffffff`) exactly; screenshot confirms cards now visually pop
against the slightly-gray `--background` gutter. 3001 — no `containerDS`
class anywhere in the DOM, untouched.

## Scan: 2026-07-28 — task card borders removed entirely (flag-on only)

Same-day follow-up to the card redesign: "remove the borders from all the
selected and unselected cards." Cards are now separated purely by the 2px
gap (`AvailableTasks.module.scss`'s `.listContainerDS`), not by an outline —
matches shadcn `Card` usage patterns where a border is optional, not
required.

- `Task.module.scss`'s `.containerDS .taskLink` base rule changed from
  `border: 1px solid var(--border)` to `border: none`. Radius and padding
  unchanged.
- **Simplified away 3 of the 4 specificity-matched override rules from the
  prior round** — with border always `none` in every state, "does the
  Carbon-legacy rule win or does mine" no longer matters for the border
  property itself (both resolve to invisible), so the `:first-child`,
  `&.active + &`, and plain `:not(.active)` overrides (which existed only
  to force `border-color` correctly) were dead weight and removed. Kept
  exactly one: `&:not(.active):last-child .taskLink { padding: $spacing }`
  — that Carbon-legacy rule (`&:not(.active):last-child .taskLink`, 4
  selector components vs. the base rule's 3) sets asymmetric padding to
  compensate for a border width that no longer exists at all, so it still
  needed an explicit same-or-higher-specificity override to keep padding
  uniform.
- `tsc --noEmit` clean (no `.tsx` changes this round — CSS-only). Live-
  verified: 3000 (flag-on) — both a plain unselected card and the active
  (selected) card report `border: 0px none` with radius/padding intact.
  3001 (flag-off) — Carbon's mixed per-side border values (top/bottom set,
  left/right differ) are unchanged, confirming the old stacked-row divider
  look wasn't touched.

## Scan: 2026-07-28 — task tile → rounded card redesign (flag-on only)

Prompted by: "update the task tiles to be rounded cards like shown in the
design system. there should be no borders between the cards. the cards
should have 2 px between them and 8 px padding around them and the container
it is in... more inline with the design system which is based off of shadcn
and less like carbon which is not rounded and things stack on top of each
other." Structural redesign, not a token swap: replaces Carbon's
stacked-row-with-shared-dividers model with the DS's own `Card` component
pattern (`rounded-xl` + `ring-1 ring-border`, read directly from
`camunda-design-system/src/components/ui/card.tsx`) — a standalone bordered,
rounded card per task, separated by a gap, not touching.

- **`Task.module.scss`'s `.containerDS`** — rewritten from color-only
  divider overrides to a full card shape: `.taskLink` now gets
  `border: 1px solid var(--border)`, rounded corners, and a constant
  `padding: $spacing` in every state (active/hover/adjacent-to-active/first/
  last), replacing Carbon's border-width-compensation logic entirely (the
  border no longer changes width between states, so there's nothing left to
  compensate for). Each new override selector was written to match or
  exceed the specificity of the specific Carbon-legacy rule it needs to
  beat (documented inline per rule — e.g. `&:first-child`, `&.active + &`,
  `&:not(.active):last-child`) rather than relying on a single blanket rule
  and hoping cascade order holds, per this session's established
  specificity-trap lesson.
  - **`--radius-xl` doesn't resolve as a real custom property in this app's
    bundled CSS** — checked live: `getComputedStyle` on both `<html>` and
    the `.c4-ui` scope shows only the base `--radius` (`0.5rem`) has an
    actual value; the derived aliases (`--radius-xl`/`-lg`/`-sm`/`-md`) are
    registered (enumerable, presumably via a Tailwind v4 `@property`
    declaration) but come back empty everywhere tested. Used the DS's own
    formula for `--radius-xl` directly instead: `calc(var(--radius) + 4px)`
    (matches `camunda-design-system/src/index.css` line 178 exactly). Worth
    a follow-up ticket if other work needs the other derived radius
    aliases — same empty-value issue would apply to `--radius-sm`/`-md`/`-lg` too.
  - `.activeDS` simplified to background-color only — border/radius/padding
    now come entirely from `.containerDS`'s shape rule (same element always
    gets both classes together, so no duplication).
- **`AvailableTasks.module.scss`'s new `.listContainerDS`** — `display:
  flex; flex-direction: column; gap: 0.125rem` (2px) for the space between
  cards, `padding: 0.5rem` (8px) for the outer gutter against the panel
  edges. Wired via `featureFlags.dsTasklistUI` in `AvailableTasks.tsx` (new
  `featureFlags` import).
- `tsc --noEmit`/`eslint` clean. Live-verified: 3000 (flag-on) — every card
  (first, last, active, hover, adjacent-to-active) has an identical 1px
  `var(--border)` border, 12px radius, 16px padding; list container has
  exactly 8px padding and 2px measured gap between adjacent card
  bounding-rects. 3001 (flag-off) — zero `*DS` classes present, list
  container padding/gap both `0`/`normal` (untouched), screenshot confirms
  the original Carbon stacked-row look (square corners, shared dividers,
  edge-to-edge) is fully intact.

## Scan: 2026-07-28 — background-color token fix, Aside "Details" header

Same-day follow-up: after the panel background fix, `Aside`'s "Details"
`ContainedList` header stood out white against the now-`--background`
panel. Same SHIM-tier situation as the `ContainedList` row-divider fix
earlier today — `kind="disclosed"` (`Aside.tsx`) compiles to
`.cds--contained-list--disclosed`, whose header background is Carbon's
`$layer` token (confirmed in `@carbon/styles`'s `_contained-list.scss`, not
guessed). Added a second `:global()` rule alongside the existing row-divider
one, nested under the same `.aside.asideDS` selector in
`taskDetailsLayoutCommon.module.scss`:
`:global(.cds--contained-list--disclosed .cds--contained-list__header) {
background-color: var(--background); }`. No `.tsx` changes needed (reuses
the existing `.asideDS` wiring). `tsc --noEmit` clean. Live-verified: 3000
header background now matches the panel exactly
(`oklch(0.985 0 0)`); 3001 unchanged (`rgb(255, 255, 255)`, Carbon's
original `$layer` white).

## Scan: 2026-07-28 — background-color token fix, task-list/details panels

Prompted by the Product Builder selecting the task-details content section
and `Aside` panel (both live inside `TasksLayoutPage.module.scss`'s
`.detailsPanel`) and asking for `--background` on these containers.

- `TasksLayoutPage.module.scss`'s `.tasksPanel` (left task-list column) and
  `.detailsPanel` (right details column) both used
  `background-color: var(--cds-layer)` unconditionally. Added
  `.tasksPanelDS`/`.detailsPanelDS` (folded into the existing
  `.detailsPanelDS` border rule) setting `background-color: var(--background)`
  — the DS's base surface token — so both panels share one background,
  matching the divider-token consistency fixed in the prior two rounds.
  Wired via `featureFlags.dsTasklistUI` in `TasksLayoutPage.tsx`
  (`.tasksPanel` had no flag branching at all before this; `.detailsPanel`
  already had its DS class from the border-token round, just extended it).
- `tsc --noEmit`/`eslint` clean. Live-verified: 3000 (flag-on) — both panels'
  computed `background-color` are now identical (`oklch(0.985 0 0)`, the
  light-mode `--background` value). 3001 (flag-off) — no `*DS` class
  present, both panels keep Carbon's original `--cds-layer` color unchanged.
- **Not yet touched** (same `var(--cds-layer)` background pattern, other
  files, out of the exact scope selected this round — flag if these should
  match too): `NoTasks.module.scss` (empty-state card),
  `CollapsiblePanel.module.scss` (Legacy-only per the confirmed-dead-code
  finding, so N/A on the DS path), `process-diagram/ProcessDiagramView.module.scss`'s
  `.diagramFrame`, `pages/TaskDetailsProcessSkeleton.module.scss`,
  `pages/TaskDetailsHistoryPage.module.scss`.

## Scan: 2026-07-28 — border-color unification pass, part 2 (Aside dividers + footer)

Follow-up to the same-day border-unification pass. Product Builder selected
specific rendered elements (via the browser's element inspector) showing two
remaining Carbon-colored dividers: the row dividers inside `Aside`'s
`ContainedList` ("Creation date" / "Candidates" / "Priority" / "Due date"),
and the border above the "Complete Task" button in `TaskDetailsTaskPage`.

- **`TaskDetailsTaskPage.module.scss`'s `.footer`** — flagged as
  out-of-thematic-scope in the prior round ("divider above the Complete Task
  button"); the Product Builder's selection confirms it IS in scope. Added
  `.footerDS` (`border-top-color: var(--border)`), wired via
  `featureFlags.dsTasklistUI` in `TaskDetailsTaskPage.tsx`.
- **`Aside`'s `ContainedList`/`ContainedListItem` row dividers** — these are
  NOT custom CSS; `ContainedList`/`ContainedListItem` are a SHIM-tier compat
  export (`carbon-compat/contained-list.tsx`: `export {ContainedList,
  ContainedListItem} from '@carbon/react'` — literally Carbon's own
  component re-exported unchanged, no DS/shadcn equivalent exists yet per the
  file's own `// MIGRATION TODO` comment). Its divider is Carbon's
  `.cds--contained-list-item:not(:last-of-type)::before` pseudo-element
  (`background-color`, not a border property — confirmed by reading
  `@carbon/styles`'s own `_contained-list.scss` source rather than guessing).
  Can't be fixed via a prop/className on the component itself since it's the
  same component regardless of flag state. Fixed with a `:global()`-scoped
  override nested under the existing `.aside.asideDS` selector in
  `taskDetailsLayoutCommon.module.scss` — `.aside.asideDS
  :global(.cds--contained-list-item:not(:last-of-type))::before {
  background-color: var(--border); }` — 3 classes vs. Carbon's 1, wins on
  specificity with no source-order dependency, and only ever matches inside
  an `<aside>` that already has `.asideDS` (i.e. only when the flag is on).
- `tsc --noEmit` and `eslint` clean. Live-verified: 3000 (flag-on) — both the
  footer border and every `ContainedList` row divider resolve to the
  identical `--border` computed color already used by the task tile and
  every other divider in the prior round. 3001 (flag-off) — no `footerDS`
  class present, `ContainedList` dividers render Carbon's original
  `--cds-border-subtle` color unchanged.

## Scan: 2026-07-28 — border-color unification pass (flag-on only)

Prompted by: "update the borders to be the correct border color and so they
all use the same border token color... the border between task tiles,
containers... I have selected most of the areas which contain the borders I
am referring to `--border`." Goal: every container/divider border on the DS
(flag-on) side resolves to the same `--border` token — previously two
DS-only rules already existed but still pointed at Carbon's
`--cds-border-subtle`, and several container dividers had no DS override at
all (still inheriting Carbon's token unconditionally).

- `taskDetailsLayoutCommon.module.scss`'s `.headerBorder` and
  `.content > [data-slot='tabs']` — these were already DS-only by
  construction (applied conditionally / scoped to a DS-only `data-slot`
  attribute), just still using `--cds-border-subtle`. Swapped the token
  value directly to `--border`, no new class or `.tsx` wiring needed.
- `taskDetailsLayoutCommon.module.scss`'s `.aside` (right panel divider),
  `TasksLayoutPage.module.scss`'s `.detailsPanel` (list/details panel
  divider), `Filters.module.scss`'s `.panelHeader` (divider above "All open
  tasks"), `AutoSelectNextTaskToggle.module.scss`'s `.container` (divider
  above/below the auto-select toggle row) — all previously unconditional
  single Carbon-token borders shared by both UIs. Added a nested `.xxxDS`
  override (`border-*-color: var(--border)`) to each, wired via
  `featureFlags.dsTasklistUI` in `Aside.tsx`/`TasksLayoutPage.tsx`/
  `Filters.tsx`/`AutoSelectNextTaskToggle.tsx` (new `featureFlags`/`cn`
  imports added to the latter two).
- `Task.module.scss`'s `.containerDS` tile-row divider was already on
  `--border` from an earlier round this session — confirmed unchanged,
  now the reference point every other container border matches.
- Not touched (out of thematic scope — decorative box borders, not
  container/panel dividers, and/or dead code on the DS path):
  `NoTasks.module.scss` (empty-state card border), `CollapsiblePanel.module.scss`
  (Legacy-only markup, DS path renders `AppSidebar` instead — confirmed dead
  earlier this session), `TaskDetailsTaskPage.module.scss`'s `.footer`
  (divider above the Complete Task button).
- `tsc --noEmit -p tsconfig.browser.json` and `eslint` clean on all touched
  files. Live-verified on 3000 (flag-on): all five new/updated DS border
  rules resolve to the identical `--border` computed color. Live-verified on
  3001 (flag-off): zero `*DS` border classes present anywhere in the DOM,
  base Carbon `--cds-border-subtle` borders unchanged.

## Scan: 2026-07-28 — DS typography/color pass, page-wide (flag-on only)

Prompted by: "did you update all the text and colors, or only that in the text
tiles? do it throughout the page specifically for 3000." Prior work (this same
session, 2026-07-27/28) had only fixed `Task.tsx`'s tile name/label fonts and
the tile's active/hover/focus color tokens. This round found every remaining
`.module.scss` file still using Carbon's `type.type-style()` mixin or
`--cds-text-*`/`--cds-icon-*`/`--cds-link-*` tokens (`grep -rl` across
`src/tasklist`), read each file's `.tsx` caller to confirm real usage, and
fixed each with a DS-only override class gated by
`featureFlags.dsTasklistUI` — same pattern as `Task.module.scss`'s
`.labelDS`/`.nameDS`: nest the new class inside the existing selector
(`&.xxxDS`) so it always wins on specificity regardless of source order,
never replace the base Carbon rule.

**Carbon → DS mapping used** (verified against `@carbon/type`'s
`_styles.scss`/`_scale.scss` source, not guessed): `caption-01`/`label-01` →
Tailwind `text-xs` (12px/16px); `label-02`/`body-short-01`/`body-01`
(=`body-long-01`) → `text-sm` (14px/20px); `body-short-02` → `text-base`
(16px/24px); `heading-01` → `text-sm` + `font-semibold`; `heading-02` →
`text-base` + `font-semibold`; `heading-03` (=`productive-heading-03`) →
`text-xl`, regular weight (Carbon's heading-03 is unusually non-bold). All
DS variants set `letter-spacing: normal` (Carbon's ramp carries an explicit
0.16–0.32px tracking the DS's own Tailwind-based type system doesn't use).
Colors: `--cds-text-primary` → `--neutral-foreground-strong`;
`--cds-text-secondary`/`--cds-icon-disabled` → `--neutral-foreground-subtle`
(no dedicated disabled-icon token exists in the DS).

**Files fixed** (all: read caller, added `.xxxDS` class, wired via
`featureFlags.dsTasklistUI` in the `.tsx`, `tsc --noEmit` + `eslint` clean,
live-verified on both the 3000 (flag-on) and 3001 (flag-off) dev servers —
zero `*DS` class ever appears on 3001's DOM):
- `AssigneeTag.module.scss`/`.tsx` — `.tag`/`.assigned`/`.highlighted` (color
  only; this Tag is compat-routed so DS Tag already supplies correct
  font-size, no font override added)
- `Filters.module.scss`/`.tsx` — `.header` ("All open tasks" title, `heading-01`)
- `NoTasks.module.scss`/`.tsx` — `.icon`/`.heading`/`.body` (empty-state)
- `HistoryTable.module.scss`/`.tsx` — `.detailsLabel` (`caption-01`, no color
  set originally, so none added)
- `ActiveTransitionLoadingText.module.scss`/`.tsx` — `.message` (`label-02`)
- `Aside.module.scss`/`.tsx` — `.itemHeading`/`.itemBody` (task-details right
  panel; 7 call sites each, `body-short-01`)
- `TaskDetailsHeader.module.scss`/`.tsx` — `.taskName` (`body-short-02`,
  weight 500 to match `Task.module.scss`'s `.nameDS` — same "task name" role
  on the tile and here), `.processName`/`.taskStatus`/`.taskAssignee`
  (`label-01`)
- `process-diagram/ProcessDiagramView.module.scss`/`.tsx` — `.processName`
  (`heading-02`)
- `NoTaskSelectedPage.module.scss`/`.tsx` — `.newUserText`/`.oldUserText`
  (color cascades to the `h3`; `body-long-01` on the `p`/`a` children)
- `TaskDetailsHistoryErrorPage.module.scss`/`.tsx` — `.title` (`heading-03`,
  no color set originally), `.description` (`body-01`)

**Skipped, with reason:**
- `CollapsiblePanel.module.scss` — confirmed dead-code-safe for the DS path:
  `CollapsiblePanel.tsx`'s DS branch renders `AppSidebar` and returns before
  reaching any code that references `.panelHeader`/`.filterItem`/etc.
  (those markup blocks are Legacy-only). No change needed.
- `process-diagram/BPMNDiagram.module.scss` (`--cds-link-inverse`) — this is
  a BPMN diagram SVG stroke color (`.tasklist-highlighted-activity .djs-outline`),
  not page text/UI color. Left out of scope for this pass; flag for a
  separate diagram-styling pass if the DS wants a matching highlight color.
- `DateLabel.module.scss`/`PriorityLabel.module.scss` — already correctly
  gated from the round-3 fix earlier this session (Legacy-only
  `.popoverHeading`/`.popoverBody`, type-style restored).

## Scan: 2026-07-27 — audit round 3: full diff scan, one severe miss found

Prompted by the Product Builder reporting the tooltip issue "still" present and
a "select button... wrong too, you used the same one on both" — pointing
directly at a bug audit round 1 should have caught but didn't.

- **`ProcessesSelect.tsx` — missed entirely in round 1.** Same class of bug as
  `LabelWithPopover.tsx`: this file bypasses the compat layer (raw DS `Select`
  primitives, for the `avoidCollisions`/`side` gap documented inline) and had
  *zero* feature-flag branching — always rendered the DS Select, flag on or
  off. Round 1's audit checked every file for un-gated `className`/prop leaks
  onto compat components, but never re-scanned for un-gated *direct DS package
  imports* beyond the one it happened to be fixing (`LabelWithPopover`) — this
  is exactly why round 3 started with a blunt, exhaustive `grep` across the
  full `git diff` for every `from '@camunda/design-system'` import, rather
  than relying on memory of which files needed checking. Fixed: split into
  `ProcessesSelectDS` (the raw-primitive version) and `ProcessesSelectLegacy`
  (the original `design-system-compat`-routed version, restored), branched on
  `featureFlags.dsTasklistUI`, mirroring the `LabelWithPopover` pattern
  exactly.
- **Verified, not just reasoned about**: ran `ProcessesSelect.test.tsx` (which
  already existed, unmodified) with `VITE_DS_TASKLIST_UI=false` forced —
  all 3 pass, including one using Playwright's native `selectOptions` (only
  meaningful against a real `<select>` element), proving the restored Legacy
  path renders genuine Carbon `Select`. Same forcing done for
  `LabelWithPopover.test.tsx`/`DateLabel.test.tsx` in round 2 — both
  confirmed via real rendered Carbon Popover markup.
- **`TabListNav.module.scss`** — deleted in the TabListNav migration
  (`Tab`/`TabList`/`Tabs` from `design-system-compat`, not a direct DS
  import). Its one rule (`:global(.cds--tabs__nav-link).hidden`) supported
  the `visible: false` case of the original hand-rolled markup — confirmed
  dead code, no current caller ever sets `visible: false`
  (`TaskDetailsLayout.tsx`'s tab items never set it). Zero observable effect;
  not restored.
- **Bigger finding surfaced by checking that**: `TabListNav.tsx`'s migration
  wasn't a like-for-like prop swap — it replaced the original hand-rolled
  `<nav>`/`<button>` markup with real `Tab`/`TabList`/`Tabs` components
  entirely. With the flag off, old-UI's tab bar now comes from Carbon's
  actual `Tabs` component (via the already-flag-gated compat exports) instead
  of the original hand-written markup — a real, structural change to old-UI's
  DOM, bigger in kind than anything else found in this audit, though it
  should render as a normal, correct Carbon tab bar (compat's `Tabs` routes
  to Carbon's real component with the flag off, same as every other
  already-migrated symbol). Asked the Product Builder explicitly rather than
  deciding unilaterally: no tabs-specific complaint had been raised for
  old-UI, so **left as-is** per their explicit choice — revisit only if
  something is actually observed wrong there.

**Process lesson, sharper than round 2's**: reasoning "I checked the files I
changed" is not the same as reasoning "I checked every file that imports the
DS package directly." Round 1 fixed `LabelWithPopover` (found by inspection)
but never asked "are there other files like this one?" — a five-second
`grep -rn "from '@camunda/design-system'"` across the full diff would have
caught `ProcessesSelect` immediately, and should be the *first* step of any
future flag-safety audit in this repo, not something arrived at only after a
second user report.

## Scan: 2026-07-27 — audit round 2: the first audit itself broke old-UI

The first audit pass (below) found and fixed 4 regressions, but the fixes
themselves introduced two more — the Product Builder caught one directly
(border missing under tabs on the old-UI/3001 preview) and asked the harder
question of *why*, which surfaced a second, more subtle one on inspection.

- **`TabListNav.tsx`** — before this migration touched the file, it passed
  `layoutStyles.tabs` (a plain border-top+bottom) to the tab bar
  unconditionally; that's harmless for Carbon's real `TabList` (naturally
  full-width, so the border always looked like a correct divider there) and
  was never gated because the file had no DS-awareness yet at that point.
  When fixing the DS pill's border (it was hugging the small `w-fit` pill
  instead of spanning the row) earlier this pass, `layoutStyles.tabs` was
  removed from `TabList`'s className entirely and replaced with a new
  `[data-slot='tabs']`-scoped rule — correct for the DS path, but it silently
  deleted the border for the Carbon path too, since nothing else was
  providing it anymore. Fixed: `layoutStyles.tabs` (renamed/kept as `.tabs`)
  is now applied to `TabList` only when `!featureFlags.dsTasklistUI`; the
  `[data-slot='tabs']` rule remains DS-only (by construction), so no double
  border.
- **`PriorityLabel.tsx`/`DateLabel.tsx`** — round 1 of this audit confirmed
  the *color* removed from `.popoverBody`/`.popoverHeading` was safe (Carbon's
  `.cds--popover-content` sets that itself). It did not check *font-size*.
  Re-checked Carbon's `_popover.scss` directly: it sets no type-style at all,
  only color/background — so old-UI's popover text was relying entirely on
  the explicit `type-style('body-short-01'/'heading-01')` that got removed
  along with the color. Fixed: restored both scss files with type-style only
  (no color, that part was genuinely safe), applied conditionally
  (`featureFlags.dsTasklistUI ? plainText : <span className={styles.x}>`) in
  both components.

**Verification this time went further than reasoning about it**: forced
`VITE_DS_TASKLIST_UI=false` for one test run (`.env.local` on this machine
always sets it `true`, so the normal test run never exercises the Legacy
path at all) to actually render the Legacy branch, not just argue it should
work. Confirmed via real rendered output — genuine Carbon Popover markup
(`cds--popover-container`, `cds--popover--open`, `cds--popover-content`,
`cds--popover-caret`), content visible on hover. 3 assertions in
`LabelWithPopover.test.tsx`/`DateLabel.test.tsx` fail against this
Legacy-path output, but both failures are the tests' own DS-only assumptions
(a `data-testid` that only exists in DS markup; assuming full unmount-on-close,
which is Radix Tooltip's behavior, not Carbon Popover's — Carbon keeps the
DOM and toggles a class instead) — not evidence of a functional break. Those
two test files have only ever run against the DS path in this environment;
this is a pre-existing test-coverage gap (no flag-off test run existed before
this check), flagged here rather than silently patched, since parametrizing
the whole suite across both flag states is a bigger call than this pass
warrants.

## Scan: 2026-07-27 — audit: did the visual cleanup pass break old-UI (flag off)?

Prompted directly by the Product Builder asking this exact question. Audited
every file touched since the visual cleanup pass began, specifically for
unconditional (non-flag-gated) code that could change old-UI's rendering.
Found and fixed three real regressions, confirmed several other suspects safe.

**Real regressions found and fixed:**

- **`LabelWithPopover.tsx`** — the Popover-to-Tooltip rewrite (see below) had
  *zero* feature-flag branching: it imported `Tooltip` directly from
  `@camunda/design-system` unconditionally, so old-UI would have shown the new
  DS tooltip instead of Carbon's Popover. Fixed: split into
  `LabelWithPopoverDS` (the Tooltip rewrite) and `LabelWithPopoverLegacy` (the
  original Carbon-Popover implementation, restored verbatim), branched on
  `featureFlags.dsTasklistUI`.
- **`AdvancedStringFilter.tsx`** — the "Business ID" label's className fix
  (`cds--label` → `text-sm font-medium`) was a plain hand-written `<label>`,
  not routed through any flag-gated component. Tailwind utility classes exist
  in the bundle regardless of the flag, so this silently changed old-UI's
  label typography too. Fixed: branched the className on the flag. Same fix
  applied to the `hiddenDropdownLabel` className on `Dropdown`.
- **`FieldsModal.tsx`** — `className={styles.modal}` / `styles.modalHeader}` /
  `styles.modalBody}` / `styles.modalFooter}` were passed unconditionally to
  `ComposedModal`/`ModalHeader`/`ModalBody`/`ModalFooter`. With the flag off
  these resolve to Carbon's real components, which also accept `className` —
  so the width/flex-column/grid overrides meant for the DS's wider modal
  would have landed on Carbon's own modal too, risking a layout conflict with
  Carbon's own `display: grid` modal CSS. Fixed: every one of these
  className props is now `featureFlags.dsTasklistUI ? styles.x : undefined`.
  Also gated the `onPointerDownOutside` escape-hatch prop the same way —
  harmless to Carbon (an unrecognized prop, not a crash) but pointless to
  send.
- **`TaskDetailsHeader.tsx`** — the border-bottom added under the header was
  on `.header`, a class shared unconditionally by both UI paths (same
  component, not duplicated). Moved the border into its own `.headerBorder`
  class, applied conditionally via `cn(layoutStyles.header,
  featureFlags.dsTasklistUI && layoutStyles.headerBorder)`.

**Checked and confirmed safe (no fix needed):**

- **`Task.module.scss`/`PriorityLabel.module.scss` `.inlineIcon`** (explicit
  16px size + `display: inline-block`) — applies unconditionally to both
  paths, but the forced values match what Carbon's own icons already render
  at by default; not a DS-specific stylistic choice, a defensive fix that
  happens to be a no-op for Carbon icons.
- **`PriorityLabel.module.scss`/`DateLabel.module.scss`** losing their
  explicit `--cds-text-primary` color on popover content — verified directly
  against Carbon's own `_popover.scss`: `.cds--popover-content` sets `color:
  $popover-text-color` at the container level already, so the explicit
  wrapper color was always redundant for the Carbon path. No visual change.
- **`taskDetailsLayoutCommon.module.scss`'s `[data-slot='tabs']` selector** —
  naturally scoped: `data-slot` is a shadcn/DS convention attribute Carbon's
  real `Tabs` component never sets, so this rule cannot match anything when
  the flag is off, by construction.
- **`ProcessesSelect.tsx`, `CollapsiblePanel.tsx`, `TabListNav.tsx`** (the
  larger rewrites) — already correctly branch on the flag (`CollapsiblePanel`
  has an explicit `if (featureFlags.dsTasklistUI)` early return;
  `TabListNav`/`ProcessesSelect` route through already-flag-gated
  `design-system-compat` exports or, for `ProcessesSelect`'s raw-primitive
  bypass, are a standalone component only ever invoked from DS-only code
  paths).

**Lesson for future work in this repo**: any file where a fix reaches for the
DS package directly (`@camunda/design-system` or plain Tailwind utility
classes on a hand-written element), rather than through the already-gated
`#/shared/design-system-compat` re-exports, needs its own explicit
`featureFlags.dsTasklistUI` branch — the compat layer's ternary-per-symbol
pattern is what makes gating automatic everywhere else, and bypassing it
(which has been necessary a few times this pass, for real compat-layer gaps)
means picking that safety back up by hand.

## Scan: 2026-07-27 — LabelWithPopover.tsx rewritten from Popover to Tooltip

Real bug, not a positioning tweak: the compat `Popover`'s `onMouseEnter`/
`onMouseLeave` were passed to `<Popover>` itself (Radix `Popover.Root`, a
context-only component with no DOM element), so they never fired at all —
hover-to-show was silently broken since the earlier layout fix in this file
(the one that wrapped everything in an outer `<span>`). Separately, even once
hover was wired to the right element, positioning was anchored to
carbon-compat's internal hidden trigger span (a zero-size phantom element
sitting at the start of the row) rather than the visible label content, and
the content box wasn't sized to its content.

Root fix: this is genuinely hover-to-show info content — a tooltip, not a
click-driven popover. Rewritten to use the DS's raw `Tooltip`/`TooltipTrigger`/
`TooltipContent`/`TooltipProvider` primitives directly (bypassing the compat
layer for this component, same category of exception as `ProcessesSelect.tsx`
above): native hover open/close (no manual state), positions relative to the
actual wrapped element via `TooltipTrigger asChild`, content sizes to fit by
default. Wrapped in a local `TooltipProvider` (same stopgap as `IconButton.tsx`
and `CollapsiblePanel.tsx` — no app-root provider exists yet). `PriorityLabel.tsx`/
`DateLabel.tsx`'s `align` prop narrowed from the old Carbon-style union to the
two values actually ever passed (`'top-start' | 'top-end'`).

Also got the existing `LabelWithPopover.test.tsx` suite running for the first
time in this session (Playwright's browser binaries weren't installed in this
environment — `npx playwright install chromium chromium-headless-shell` fixed
that) and updated it for the new implementation: Radix Tooltip renders an
always-in-DOM visually-hidden accessibility announcer span alongside the real
floating content, so `getByText`/`getByRole('tooltip')` matched both
ambiguously — added a `data-testid` on the real `TooltipContent` instead.
The mouse-leave test also needed an explicit hover-elsewhere after unhovering
the trigger: Radix intentionally keeps a tooltip open if the pointer moves
from the trigger straight into the content ("hoverable content"), so the test
needs to prove the pointer left the whole region, not just the trigger. All 4
tests pass.

> **DS feedback to report** has moved to the very bottom of this file, so it is
> easy to find without scrolling through the scan log. See the last section.

## Scan: 2026-07-25 — visual cleanup pass (multiple files)

Ad hoc fixes made during a post-migration visual review, requested directly by
the pod's Product Builder rather than found via `/migrate`. Listed together
since none of them were part of the original bulk-migration batches.

- **`Task.module.scss` / `PriorityLabel.module.scss`** — `.inlineIcon` had no
  explicit size, so Lucide's 24px default (vs. Carbon's 16px) blew up row
  height and threw off alignment. Also missing `display: inline-block`:
  Tailwind preflight resets all `<svg>` to `display: block`, which inside
  inline label text forces a line break, stacking icon above text instead of
  beside it. Fixed both: explicit `width/height: 1rem` + `display: inline-block`.
- **`LabelWithPopover.tsx`** — real bug in `carbon-compat/popover.tsx`: it
  renders a hidden trigger `<span>` as a sibling next to the real content
  instead of wrapping both in one container, so any inline `Popover` usage
  leaks an extra invisible flex item into the parent layout. Wrapped
  `LabelWithPopover`'s whole return in one `<span>` so it always yields
  exactly one DOM node to its parent flex row, regardless of Popover's
  internals. Fixes date-row left-alignment on the task tile.
- **`FieldsModal.module.scss`** — `.twoColumnGrid` used `align-items:
  flex-end`, bottom-aligning the Assignee/Status fieldsets instead of
  top-aligning their headers. Changed to `start`. Also widened the modal
  (`.modal { max-width: 45rem }`, was capped at the DS default `sm:max-w-sm`
  ~384px) since the two-column layout with nested sub-grids (Due/Follow-up
  date, Task ID/Business ID) overflowed it. DS's `Modal`/`ComposedModal`
  `size` prop is dropped entirely by `carbon-compat/modal.tsx` (confirmed via
  `warnDroppedProps`) — no size scale exists, this has to be a manual
  `className` width override per-modal.
- **`.modalFooter`** — was `grid-template-columns: 40% 20% 20% 20%`, stretching
  Reset's clickable area across 40% of the (now wider) modal while its label
  centered inside, reading as an unresponsive dead zone. Changed to
  `1fr auto auto auto` with `justify-self: start` on the first child so Reset
  sizes to content and sits flush left; Cancel/Save/Apply stay grouped right.
  Also made the layout a flex column (`.modal`) with `.modalHeader`/`.modalBody`
  pinning header/footer and scrolling only the body — `ModalBody`/`ModalHeader`/
  `ModalFooter` are plain stacked divs in carbon-compat with no flex layout of
  their own, so the DS Dialog's `max-h-[calc(100dvh-2rem)]` scrolled the whole
  header+body+footer together instead of just the growing body (e.g. when
  adding task variables). Added an explicit `max-height: 85vh` on `.modal`
  directly too, alongside the DS's own `calc(100dvh-2rem)`.
- **`AdvancedStringFilter.tsx`/`.module.scss`** — `Dropdown`'s `hideLabel` prop
  is destructured but never actually used in `carbon-compat/dropdown.tsx` —
  `titleText` always renders as a visible label regardless. Since the call
  site had no `aria-label` (accessibility depended entirely on that visible
  label), added an explicit `aria-label` then hid the now-redundant duplicate
  label visually via a scoped `.hiddenDropdownLabel > label { display: none }`
  rule. Also fixed the manually-written "Business ID" label using the stale
  Carbon `cds--label` class instead of the DS's own `text-sm font-medium`
  convention (visibly thinner/smaller than sibling labels).
- **`ProcessesSelect.tsx`** — rewritten to use the DS's raw `Select`/
  `SelectContent`/`SelectItem`/`SelectTrigger`/`SelectValue` primitives
  directly (all public exports from `@camunda/design-system` root) instead of
  the `carbon-compat` `Select` wrapper. Reason: `carbon-compat/select.tsx` and
  `dropdown.tsx` both use a fixed prop allowlist with no passthrough to
  `SelectContent` — there is no way to pass Radix's `avoidCollisions`/`side`
  through the compat layer, so a long options list (process definitions, can
  run into the hundreds) would sometimes flip the dropdown above its trigger
  with no way to stop it. This is the one place in the migration where the
  compat layer was bypassed outright rather than worked around; kept the same
  external prop surface so the call site in `FieldsModal.tsx` didn't change.
  Also fixed: `resolvedValue` now defaults to `'all'` instead of `undefined`
  when the field is unset, so "All processes" shows its checkmark by default
  (display-only — `prepareCustomFiltersParams.ts` already treats `''` and
  `'all'` as equivalent, so this doesn't touch filtering behavior).
- **`c4-ui.css`** — two real app-wide bugs found here, not scoped to one file:
  1. Select dropdowns had no height cap (`max-h-(--radix-select-content-available-height)`
     fills available viewport space) — added a global `max-height: 23rem`
     (~10 rows) on `[data-slot='select-content']`, same reasoning as the
     `ProcessesSelect` fix above: no per-instance prop channel exists through
     `carbon-compat`.
  2. **Font-family regression**, present since the original coexistence fix
     landed 2026-06-16, affecting every DS button/input/select across both
     Tasklist and Operate: the "Carbon form-control coexistence" rule used
     `font: revert` (the full shorthand) to fix a DataTable/toolbar
     height-calibration issue, which also reverts `font-family` — silently
     falling every DS control back to the browser's UA default (renders as
     Arial) instead of the DS's Geist Sans. Narrowed to `font-size: revert;
     line-height: revert;` (the two sub-properties actually needed for the
     height fix), leaving `font-family` alone.
- **`FieldsModal.tsx`** — `preventCloseOnClickOutside` is dropped by
  `carbon-compat/modal.tsx` (confirmed via `warnDroppedProps`) — it never did
  anything. Removed it and added `onPointerDownOutside={(event) =>
  event.preventDefault()}` instead, which isn't in `ComposedModal`'s prop
  allowlist so it passes through the wrapper's own `...rest` spread straight
  to the underlying Radix `DialogContent`, which does support it. Needed a
  type-assertion escape hatch (`as Record<string, unknown>`) since Carbon's
  own `ComposedModalProps` type doesn't know about this prop even though it
  works at runtime.
- **`TabListNav.tsx`** — was hand-written raw Carbon markup (`cds--tabs`
  classes on plain `<nav>`/`<button>` elements), never actually using
  Carbon's own `Tabs` React component — so it was never touched by any
  `/migrate` pass despite looking like Carbon UI. Migrated to
  `Tabs`/`TabList`/`Tab` from `#/shared/design-system-compat` (new
  feature-flagged exports added). These are route-driven tabs, not
  content-switching ones — `Tabs`' index-based `selectedIndex`/`onChange` API
  is used only to drive `navigate()`, there's no `TabPanel`/`TabPanels`.
  Deleted the now-orphaned `TabListNav.module.scss` (its only rule styled a
  `hidden` class this rewrite no longer needs — items are filtered out
  instead of rendered-but-hidden).
- **`CollapsiblePanel.tsx`** (the left filter nav) — bigger change, flagged
  separately: migrated to the DS's `AppSidebar` composite
  (`src/components/ui/app-sidebar.tsx`), which is a `position: fixed` global
  nav-rail component (its own collapse/expand, resizable width, mobile Sheet
  overlay), not a like-for-like swap for this in-page filter panel. Per
  explicit sign-off from the Product Builder: kept the panel in-flow (Product
  Builder chose this over restructuring `TasksLayoutPage`'s grid to
  accommodate a true fixed rail) — overrode `position: fixed` back to
  `static` via a new `.dsSidebar` class with `!important` (justified: these
  are the vendored component's own unconditional utility classes, not
  something reliably out-cascadable by source order alone). `useSidebar()`
  returns `null` gracefully with no `SidebarProvider` in the tree, so no
  provider was wired up — confirmed from `sidebar-provider.tsx` source before
  proceeding.
  - **Real design change, not just code migration:** `AppSidebar`'s
    `SidebarItem` requires an icon (no icon-less variant exists) — this panel
    previously had none. Chose icons matching existing semantic precedent
    already in this codebase: `CircleDashed` for Unassigned (matches
    `CircleDashIcon`/`AssigneeTag`), `CircleCheck` for Completed (matches
    `CheckmarkFilledIcon`), `CircleUser` for Assigned to me (matches
    `UserAvatarIcon`), `Filter` for custom filters (matches this file's own
    `FilterIcon`). No existing precedent for "All open tasks" — `ListTodo` is
    a new, unprecedented choice. Worth a design review.
  - **Known minor behavior gap:** the original expanded view showed a native
    `title` tooltip on custom filter names longer than 17 characters.
    `AppSidebar`'s `NavItemRow` only wraps items in a `Tooltip` when the rail
    is collapsed (`if (!expanded && !isMobile)`) — when expanded, a
    non-link item (this uses `onClick` navigation, not `linkProps.href`, so
    `isLink` is false) gets no path to attach a `title` attribute. Long custom
    filter names lose their expanded-view overflow tooltip. Not fixable
    without patching the DS package.
  - **Not visually verified.** Both the local dev-server preview tool and the
    Chrome extension were unreachable for this entire stretch of work — typecheck
    and lint are clean, but this is the largest, highest-risk change of the
    session (a full composite swap, not a prop/CSS tweak) and should get a
    close visual pass once tooling reconnects, before relying on it.

## Scan: 2026-07-21 — webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/available-tasks/components/PriorityLabel.tsx

### CSS token debt

- **File:** `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/available-tasks/components/PriorityLabel.module.scss`
- **Tokens found:** `--cds-text-primary` (`.popoverBody`), `--cds-spacing-01` (`.inlineIcon`)
- **Reason skipped:** `--cds-*` usage is inside a `.module.scss` file, not an inline `style=` value in JSX — out of scope for the inline-replacement audit per `docs/kb/carbon-css-token-audit.md`.
- **Suggested approach:** replace `var(--cds-text-primary)` with `var(--foreground)` and `var(--cds-spacing-01)` with the DS spacing scale equivalent when this SCSS module is next touched.
- **Shim applied:** No (out of scope, not touched)

## Deferred — HOT file (2026-07-22)

- `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/available-tasks/components/custom-filters/AdvancedStringFilter.tsx` — modified 16 days ago (2026-07-06, "refactor: move operator config out of component in OC webapp"; 2026-07-02 feature commit adding it). Inside the 30-day HOT window — deferred per the migration agent's own safety gate. Revisit after 2026-08-05 (30 days from last touch), or sooner if coordinated with whoever owns that feature work.
  - Findings recorded for when it's revisited: `TextInput` is a clean SWAP. `Dropdown` is classified REMAP in `mapping.json` but the `carbon-compat/dropdown.tsx` adapter is actually prop-compatible (same pattern as `OverflowMenu` — no JSX restructuring needed). One real risk to check when migrating: the adapter silently drops `size` and `direction` props; this file passes `direction="top"`, which the DS's Radix-backed Select doesn't support — the dropdown may open downward instead of upward, risking viewport clipping depending on where the filter renders. Needs a manual visual check once unblocked.
- `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details/components/process-diagram/ProcessDiagramView.tsx` — modified 19 days ago (2026-07-02, "feat: migrate task details process tab"). Pre-flight analysis mis-bucketed this as a safe bulk-scope target (score 0.75, SWAP+SHIM only); independent git-log check inside this agent run found it inside the 30-day HOT window — deferred per the migration agent's own safety gate, not touched. The sibling `DiagramControls.tsx` in the same directory shares the identical last-touch commit/date and is HOT for the same reason (confirms the tip that same-feature-area siblings move together). Revisit after 2026-08-01 (30 days from last touch), or sooner if coordinated with whoever owns the process-diagram feature work.
  - Findings recorded for when it's revisited: both symbols are low-risk. `Tag` is SWAP — `carbon-compat/tag.d.ts` reuses Carbon's own `TagProps` type, fully prop-compatible, no JSX changes needed. `Layer` is SHIM — `carbon-compat/layer.d.ts` re-exports Carbon's `Layer` unchanged (paper-move only, not yet shadcn-native); swap the import path, no prop changes. `src/shared/design-system-compat/index.ts` does not yet export either symbol — both need new named exports added there (Tag from `@carbon/react` + `@camunda/design-system/carbon-compat`, feature-flagged on `featureFlags.dsTasklistUI`; Layer likewise, sourced from `@camunda/design-system/carbon-compat`) following the existing pattern before this file's imports can be repointed to `#/shared/design-system-compat`.

- `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details/components/AsyncActionButton/AsyncActionButton.tsx` — modified 2026-06-30 ("fix: add clearTimeout guard"), preceded by 2026-06-29 ("feat: migrate tasklist task assignment"); sibling test touched 2026-07-01. Inside the 30-day HOT window (in-flight task-assignment feature work). Deferred — revisit after ~2026-07-30, or coordinate with the feature owner.

- `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details-history/components/HistoryItemDetailsModal.tsx` — modified 2026-07-08 ("refactor: fix history details modal structure", real content change by Vinicius Goulart). 14 days old, inside HOT window. Deferred. Analysis done ahead of time: ComposedModal/ModalHeader/ModalBody already have compat exports (prop-compatible, no restructuring needed); StructuredList* would need new exports; icons EventSchedule→CalendarClock, UserAvatar→User (lucide fallback, no registry match). Ready to run once cooled or cleared by the developer.

- `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details/components/TurnOnNotificationPermission.tsx` — modified 2026-06-25 ("feat: migrate task details layout to unified webapp"), 27 days old, inside HOT window. Deferred.
  **Separately, a real blocker regardless of HOT status:** `carbon-compat/actionable-notification.tsx` in the DS package is not actually implemented yet — it's a bare passthrough re-export of `@carbon/react`'s `ActionableNotification`, explicitly marked `// MIGRATION TODO: adapter pending Alert shadcn component`. `mapping.json`/`carbon-migration-tiers.md` both call this REMAP-ready, but the shipped adapter doesn't back that up. This one can't be meaningfully migrated at all until the DS team ships the real adapter — worth flagging to them directly, separate from this app-repo migration.

- `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details/components/process-diagram/DiagramControls.tsx` — created 2026-07-02 ("feat: migrate task details process tab", Vinicius Goulart), 20 days old, inside HOT window. Deferred. Analysis done ahead: `IconButton` is a genuine REMAP needing JSX restructuring (Button+Tooltip composition, not a drop-in) — will need a small wrapper component in design-system-compat, not the simple ternary pattern. Icons: Add→ZoomIn, Subtract→ZoomOut, CenterCircle→Crosshair (all lucide fallback, no registry match).

## Deferred — entire opus tier, all HOT (2026-07-22, checked directly, not dispatched)

Checked git history for all 9 opus-tier files before dispatching any migration agent — every one falls inside the 30-day HOT window, confirming this is the same broad in-flight "migrate to unified webapp" effort seen across the sonnet tier. Skipped dispatching agents for these (would have just re-confirmed HOT status at token cost). Revisit each individually once its last-touch date clears 30 days, or once coordinated with the feature owner.

- `modules/available-tasks/components/custom-filters/FieldsModal.tsx` — 2026-07-02, "feat: add AdvandedStringFilter to OC Tasklist for Business ID". Also has icon imports (`Close`, `Add`) not counted by the analyzer.
- `modules/available-tasks/components/CollapsiblePanel.tsx` — 2026-06-19, "feat: migrate custom filters no unified webapp". Icons: `Filter`, `SidePanelClose`, `SidePanelOpen`. Also has a FLAG-tier symbol (`ButtonSet`) per the bulk scope.
- `modules/task-details-history/components/HistoryTable.tsx` — 2026-07-08 (most recent), "refactor: turn history details button into link". Icon: `Information`. Has a FLAG-tier symbol (`DataTableHeader`) and penalty symbols (`DataTable`, `TableContainer`) per the bulk scope.
- `modules/task-details/components/DetailsSkeleton.tsx` — 2026-06-25, "feat: migrate task details layout to unified webapp". Has a FLAG-tier symbol (`ButtonSkeleton`) per the bulk scope.
- `modules/task-details/components/Aside.tsx` — 2026-07-01, "feat: add Business ID in OC Tasklist".
- `modules/task-details/components/TaskDetailsLayout.tsx` — 2026-07-06, "test: add task details history modal tests".
- `modules/task-details/useTaskCompletion.ts` — 2026-07-03, "refactor: enhance state machine state". Bulk scope flagged this file's only import (`useQueryClient`) as FLAG-tier, which is unusual — worth double-checking that's a real Carbon/DS-relevant symbol and not an analyzer misfire once revisited.
- `pages/NoTaskSelectedPage.tsx` — 2026-06-19, "feat: migrate custom filters no unified webapp".
- `pages/TaskDetailsHistoryPage.tsx` — 2026-07-06, "refactor: generalize route params".

- `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/pages/TaskDetailsProcessError.tsx` — modified 2026-07-06 ("refactor: simplify error pages"), preceded by 2026-07-02 ("feat: migrate task details process tab"). 16 days old, inside HOT window, same active developer/window as other deferrals. Deferred. Button/InlineNotification already have compat exports ready for when this clears.

- `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details/components/TaskDetailsHeader.tsx` — modified 2026-06-25 ("feat: migrate task details layout to unified webapp"), 27 days old, inside HOT window. Deferred. Icon mapping confirmed for later: CheckmarkFilled → CircleCheck (documented in carbon-icons-to-lucide.md, not a guess).

## Deferred — HOT file (2026-07-22), continued

- `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/available-tasks/components/Task.tsx` — last committed 2026-07-08 (`8f10dff4555` "fix: preserve filters on task navigation", preceded same day by `011420bfdc7` "refactor: simplify task component"; also touched 2026-07-01 "feat: add Business ID in OC Tasklist" and 2026-06-25 "feat: migrate task details layout to unified webapp"). 14 days old — inside the 30-day HOT window. Deferred per the migration agent's own safety gate. This is a core, high-visibility file (renders every task row in the list), so the git-log recency check was run explicitly rather than trusting the pre-flight import-count score (which only saw `Stack` and scored 1.0 — it does not account for recency or for the four `@carbon/react/icons` imports). No edits were made to this file or to `src/shared/design-system-compat/index.ts` on its behalf. Revisit after 2026-08-07 (30 days from last touch), or sooner if coordinated with whoever owns the recent task-navigation/filter work.
  - Findings recorded for when it's revisited: `Stack` is SWAP-tier and already has a ready-made feature-flagged export in `src/shared/design-system-compat/index.ts` (`CompatStack`/`CarbonStack`) — no new export needed for that one, just repoint the import.
  - Icon findings (none exported from `design-system-compat/index.ts` yet — all four need new named exports added there following the existing `CarbonX`/`LucideX`/ternary pattern):
    - `Calendar` (creation-date icon, no color prop) → Lucide `Calendar` — identical name, trivial REMAP, generic UI concept (not a domain entity, no registry match).
    - `CheckmarkFilled` (completion-date icon, `color="green"`) → Lucide `CircleCheck` — documented in `carbon-icons-to-lucide.md`'s "Common remaps" table. Carbon's icon is filled by name; pair with `className="fill-current"` plus a semantic color utility (`text-success-foreground-strong`) instead of carrying over the raw `color="green"` string, per DS semantic-token rules (AGENTS.md rule 2) and the KB's "filled variant" guidance.
    - `Warning` (overdue-date icon, `color="red"`) → Lucide `TriangleAlert` — not a literal row in `carbon-icons-to-lucide.md` (only `WarningAlt`, `WarningAltFilled`, `WarningFilled`, `Caution` are listed, all → `TriangleAlert`); treating bare `Warning` as the same family is consistent with the existing `CriticalIcon` precedent already in this same `design-system-compat/index.ts` file (`Critical` → `TriangleAlert`, commented as a family/closest-match call, not a literal table row). Pair with `fill-current` + `text-danger-foreground-strong` in place of `color="red"`.
    - `Notification` (follow-up-date icon, `color="blue"`) → no table entry at all — genuine gap. Closest Lucide equivalent is `Bell` (fallback tier per `carbon-icons-to-lucide.md`'s "No-equivalent → placeholder" rule, since a real closest-match exists this is a documented fallback rather than `CircleDashed`). Pair with `fill-current` + `text-info-foreground-strong` in place of `color="blue"`. Log this as an icon gap when applied.
  - No FLAG-tier symbols found in this file.

- `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/pages/TaskDetailsHistoryErrorPage.tsx` — modified 2026-07-06 ("test: add task details history tab tests"), preceded by 2026-07-03 feature commit. 16 days old, inside HOT window, same active window as the rest of task-details-history. Deferred. Icon mapping confirmed for later: Launch → ExternalLink (documented in carbon-icons-to-lucide.md).

## Scan: 2026-07-23 — webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details/components/process-diagram/ProcessDiagramView.tsx

HOT hold lifted (investigation confirmed the legacy-to-unified-webapp migration underlying this file is code-complete). Migrated: `Tag`, `Layer` (both SWAP, reused existing compat exports, no JSX changes). No FLAG-tier symbols in this file.

### CSS token debt

- **File:** `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details/components/process-diagram/ProcessDiagramView.module.scss`
- **Tokens found:** `--cds-spacing-03`, `--cds-spacing-05` (x2), `--cds-text-primary`, `--cds-layer`
- **Reason skipped:** `--cds-*` usage is inside a `.module.scss` file, not an inline `style=` value in JSX — out of scope for the inline-replacement audit per `docs/kb/carbon-css-token-audit.md`. No inline `style=` tokens found in the `.tsx` file itself.
- **Suggested approach:** resolve to DS tokens/Tailwind when this SCSS module is next touched (`--cds-spacing-*` → Tailwind spacing scale, `--cds-text-primary` → `text-foreground`/`var(--foreground)`, `--cds-layer` → `bg-card`/`var(--card)` per the `--cds-layer-01` row).
- **Shim applied:** No (out of scope, not touched)

## Scan: 2026-07-23 — webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details-history/components/HistoryItemDetailsModal.tsx

HOT hold lifted (investigation confirmed the legacy-to-unified-webapp migration underlying this file is code-complete). Migrated: `ComposedModal`/`ModalHeader`/`ModalBody` (SWAP, reused existing compat exports, no JSX changes), `StructuredListWrapper`/`StructuredListBody`/`StructuredListRow`/`StructuredListCell` (SWAP, new compat exports added), `EventSchedule`→`EventScheduleIcon`/`CalendarClock` (REMAP, new compat export added), `UserAvatar`→`UserAvatarIcon` (REMAP, reused existing compat export). No FLAG-tier symbols in this file.

### CSS token debt

- **File:** `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details-history/components/HistoryItemDetailsModal.module.scss`
- **Tokens found:** `--cds-spacing-02` (gap), `--cds-spacing-05` (padding)
- **Reason skipped:** `--cds-*` usage is inside a `.module.scss` file, not an inline `style=` value in JSX — out of scope for the inline-replacement audit per `docs/kb/carbon-css-token-audit.md`.
- **Suggested approach:** replace with the DS spacing scale equivalents when this SCSS module is next touched.
- **Shim applied:** No (out of scope, not touched)

### Note on prior UserAvatar icon guidance

The 2026-07-22 deferral entry above (line 20 in this file) recorded `UserAvatar`→`User` (Lucide fallback, no registry match) as the researched choice for this file. When implementing, `src/shared/design-system-compat/index.ts` already had an established export for this exact Carbon symbol from an earlier migration (`UserAvatarIcon`, mapped to Lucide `CircleUser` — see the inline comment there for the outlined/filled rationale). Reused that existing export instead of adding a second, differently-mapped export for the same source icon, to avoid two Lucide icons representing the same Carbon glyph across the compat layer. No behavior change versus the researched choice (both are Lucide fallbacks with no registry match) — only the specific Lucide icon differs (`CircleUser` vs `User`).

## Scan: 2026-07-23 — webapp/client/apps/orchestration-cluster-webapp/src/tasklist/pages/TaskDetailsHistoryErrorPage.tsx

HOT hold lifted (investigation confirmed the legacy-to-unified-webapp migration underlying this file is code-complete). Migrated: `Button`, `Stack` (SWAP, reused existing compat exports, no JSX changes), `Layer` (SWAP/shim subtype — `carbon-compat` re-exports `Layer` unchanged from `@carbon/react`, new compat export added, no JSX changes), `Link` (SWAP, new compat export added, no JSX changes), `Launch`→`LaunchIcon`/`ExternalLink` (REMAP, new compat export added — matches the mapping confirmed in the 2026-07-22 deferral entry for this same file, above). No FLAG-tier symbols in this file.

### CSS token debt

- **File:** `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/pages/TaskDetailsHistoryErrorPage.module.scss`
- **Tokens found:** `--cds-spacing-07` (padding, `.container` and `.card` rules)
- **Reason skipped:** `--cds-*` usage is inside a `.module.scss` file, not an inline `style=` value in JSX — out of scope for the inline-replacement audit per `docs/kb/carbon-css-token-audit.md`.
- **Suggested approach:** replace with the DS spacing scale equivalent when this SCSS module is next touched.
- **Shim applied:** No (out of scope, not touched)

## Scan: 2026-07-23 — webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details/components/process-diagram/DiagramControls.tsx

HOT hold lifted (investigation confirmed the legacy-to-unified-webapp migration underlying this file is code-complete, near line-for-line against the legacy version). Migrated: `IconButton` (REMAP — `Button size="icon"` + `Tooltip` composition; no drop-in carbon-compat adapter exists, so a new composed wrapper was added at `src/shared/design-system-compat/IconButton.tsx` and re-exported through `index.ts`, rather than the plain ternary pattern used for other symbols in that file). Icons: `CenterCircle`→`CenterCircleIcon`/`Crosshair`, `Add`→`AddIcon`/`ZoomIn`, `Subtract`→`SubtractIcon`/`ZoomOut` (all REMAP, new compat exports added, Lucide fallback — no registry match; context-specific choice for diagram zoom controls, not the generic `carbon-icons-to-lucide.md` "Common remaps" row for bare `Add`). No FLAG-tier symbols in this file.

### IconButton wrapper — design notes

- `carbon-compat/MAPPING.md`'s "IconButton" row documents the full prop transform (`kind`→`variant`, `label`→ tooltip content + `aria-label` fallback, `align`→`TooltipContent side` prefix-mapped, `enterDelayMs`→`TooltipProvider delayDuration`, `autoAlign`/`badgeCount`/`highContrast` dropped) — the wrapper implements exactly this table, no invented behavior.
- The wrapper renders its own `TooltipProvider` per instance. The DS `Tooltip` primitive ships without a built-in provider by design (per its own source comment, it expects "exactly one `TooltipProvider` at the app/page root") — this repo (`main.tsx`) does not have one yet, and adding it is out of scope for a single-file migration. Sibling providers (not nested) don't trigger the open-state misfire the DS docs warn about, so this is safe short-term. **Follow-up:** once a repo-wide `TooltipProvider` exists at the app root, remove the per-instance provider from this wrapper and rely on the shared one.
- `IconButtonProps` in the wrapper is a deliberately narrower, explicitly-typed subset of Carbon's real `IconButtonProps` (native button attributes + `kind`/`size`/`align`/`label`/`enterDelayMs`) rather than a full pass-through of Carbon's type — this avoids forwarding anchor-only props (`href`, `rel`, `target`) that don't exist on `@camunda/design-system`'s `Button` (a native `<button>` component), which would otherwise be a type mismatch. `DiagramControls.tsx`'s actual usage (`className`, `size`, `kind`, `align`, `label`, `aria-label`, `onClick`, `children`) is fully covered.

### CSS token debt

- **File:** `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details/components/process-diagram/DiagramControls.module.scss`
- **Tokens found:** `--cds-spacing-05`, `--cds-spacing-09`, `--cds-spacing-02` (positioning/margin), `--cds-background` (×2, button backgrounds)
- **Reason skipped:** `--cds-*` usage is inside a `.module.scss` file, not an inline `style=` value in JSX — out of scope for the inline-replacement audit per `docs/kb/carbon-css-token-audit.md`.
- **Suggested approach:** replace with the DS spacing scale and `bg-background`/`var(--background)` equivalents when this SCSS module is next touched.
- **Shim applied:** No (out of scope, not touched)

## Scan: 2026-07-23 — webapp/client/apps/orchestration-cluster-webapp/src/tasklist/pages/TaskDetailsProcessError.tsx

HOT hold lifted (investigation confirmed the legacy-to-unified-webapp migration underlying this file is code-complete; commit history tails into "refactor: simplify error pages" — cleanup, not open construction). Migrated: `Button` (SWAP, reused existing compat export, no JSX changes), `InlineNotification` (REMAP tier per `docs/kb/carbon-migration-tiers.md`, but the shipped `carbon-compat` adapter is Carbon-shaped — its exported type is `CarbonInlineNotificationProps` verbatim, with `kind`/`subtitle`/`hideCloseButton`/`role` mapped internally — so this call site needed zero JSX changes, only the import swap; reused existing compat export). Both symbols already existed as feature-flagged exports in `src/shared/design-system-compat/index.ts` — no new exports added. No FLAG-tier symbols in this file.

### CSS token debt

- **File:** `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/pages/TaskDetailsProcessError.module.scss`
- **Tokens found:** `--cds-spacing-05` (gap, padding on `.container`)
- **Reason skipped:** `--cds-*` usage is inside a `.module.scss` file, not an inline `style=` value in JSX — out of scope for the inline-replacement audit per `docs/kb/carbon-css-token-audit.md`.
- **Suggested approach:** replace with the DS spacing scale equivalent when this SCSS module is next touched.
- **Shim applied:** No (out of scope, not touched)

## Scan: 2026-07-23 — webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/available-tasks/components/Task.tsx

HOT hold lifted (investigation confirmed the legacy-to-unified-webapp migration underlying this file is code-complete; commit history tails into "fix: preserve filters on task navigation" and "refactor: simplify task component" — cleanup/fix, not open construction; the apparent size reduction is from extracting helpers into their own tested files, not missing logic). Migrated: `Stack` (SWAP, reused existing compat export, no JSX changes). Icons (all REMAP, `@carbon/react/icons` → `lucide-react`, per `docs/kb/carbon-icons-to-lucide.md`): `Calendar`→`CalendarIcon`/Lucide `Calendar` (identical name, new compat export added); `CheckmarkFilled`→`CheckmarkFilledIcon`/Lucide `CircleCheck` (new export already existed in `src/shared/design-system-compat/index.ts` from the concurrent `TaskDetailsHeader.tsx` migration — reused as-is, added `fill-current text-success-foreground-strong` at the call site per the export's own comment); `Warning`→`WarningIcon`/Lucide `TriangleAlert` (new compat export added, consistent with the existing `Critical`→`TriangleAlert` precedent already in the file; added `fill-current text-danger-foreground-strong` at the call site); `Notification`→`NotificationIcon`/Lucide `Bell` (new compat export added — genuine gap, not in `carbon-icons-to-lucide.md` at all; fallback tier; added `fill-current text-info-foreground-strong` at the call site — see "Icon gaps" entry below). No FLAG-tier symbols in this file.

### Icon gaps (Carbon → Lucide)

- **Carbon icon:** `Notification` (from `@carbon/react/icons`)
- **Context:** follow-up-date indicator icon in the task row date strip (`Task.tsx`)
- **Chosen Lucide fallback:** `Bell`
- **Reason:** not listed in `docs/kb/carbon-icons-to-lucide.md`'s mapping table (no domain-entity registry match, no generic-UI-icon row). `Bell` is the closest visual/semantic equivalent for a notification/reminder glyph — used per the KB's "no reasonable equivalent" fallback guidance.
- **Follow-up:** consider adding `Notification` → `Bell` as a documented row in `docs/kb/carbon-icons-to-lucide.md`'s "Common remaps" table so future migrations don't need to re-derive this choice.

### CSS token debt

- **File:** `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/available-tasks/components/Task.module.scss`
- **Tokens found:** `--cds-spacing-05` (`$spacing`, used throughout `.container`/`.taskLink` padding and borders), `--cds-text-secondary` (`.label`), `--cds-text-primary` (`.name`), `--cds-layer-selected` (`.container.active .taskLink`), `--cds-border-interactive` (`.container.active .taskLink`), `--cds-layer-hover` (`.container:not(.active):hover .taskLink`), `--cds-border-subtle-selected` (×2), `--cds-focus` (`.taskLink:focus`)
- **Reason skipped:** `--cds-*` usage is inside a `.module.scss` file, not an inline `style=` value in JSX — out of scope for the inline-replacement audit per `docs/kb/carbon-css-token-audit.md`.
- **Suggested approach:** resolve to DS tokens/Tailwind when this SCSS module is next touched — `--cds-spacing-05` → the DS spacing scale, `--cds-text-secondary`/`--cds-text-primary` → `text-muted-foreground`/`text-foreground` (`var(--muted-foreground)`/`var(--foreground)`), `--cds-layer-hover`/`--cds-layer-selected` → nearest `bg-*` surface token, `--cds-border-interactive`/`--cds-border-subtle-selected`/`--cds-focus` → nearest `border-*`/`ring-*` token per the mapping table.
- **Shim applied:** No (out of scope, not touched)

## Scan: 2026-07-23 — webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details/components/DetailsSkeleton.tsx

HOT hold lifted (investigation confirmed the legacy-to-unified-webapp migration underlying this file is code-complete). Migrated: `SkeletonText` (SWAP, reused existing compat export, no JSX changes), `TabsSkeleton` (SWAP, new compat export added — `carbon-compat` ships a Tailwind `animate-pulse` adapter, no JSX changes), `ContainedList`/`ContainedListItem` (SHIM, new compat exports added — `carbon-compat` re-exports both unchanged from `@carbon/react`, no JSX changes), `Section` (SHIM, reused existing compat export added by a concurrent sibling migration, no JSX changes). `ButtonSkeleton` left on `@carbon/react` — see FLAG entry below.

## FLAG symbols

### webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details/components/DetailsSkeleton.tsx:29 — ButtonSkeleton

- **Tier:** FLAG
- **Reason:** No `carbon-compat` adapter exists for `ButtonSkeleton` — it is not exported anywhere under `@camunda/design-system/dist/carbon-compat` (confirmed by direct grep of the installed package). No DS equivalent to swap or shim to. Not silently approximated per FLAG protocol.
- **Suggested approach:** requires DS input — either ship a `ButtonSkeleton` adapter in `carbon-compat` (a button-shaped `animate-pulse` placeholder, mirroring the existing `TabsSkeleton` adapter pattern), or provide a DS-native loading-button skeleton. Once available, add a feature-flagged export to `src/shared/design-system-compat/index.ts` and repoint this file's remaining `@carbon/react` import.
- **Shim applied:** No
- **Closest DS component:** none shipped; nearest analog is the `carbon-compat` `TabsSkeleton` adapter (Tailwind `animate-pulse` placeholder) — same technique would apply to a button-shaped skeleton.
- **Current state in file:** import isolated to its own `@carbon/react` line with a `// FLAG:` comment; usage at the call site carries a `{/* FLAG: ... */}` marker. Import/usage left functionally untouched; the file still renders identically.

### webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/available-tasks/components/CollapsiblePanel.tsx:258 — ButtonSet

- **Tier:** FLAG
- **Reason:** No `carbon-compat` adapter exists for `ButtonSet` — it is not exported anywhere under `@camunda/design-system/dist/carbon-compat` (no `button-set.*` module; confirmed by direct grep of the installed package). No DS equivalent to swap or shim to. Not silently approximated per FLAG protocol.
- **Suggested approach:** requires DS input — either ship a `ButtonSet` adapter in `carbon-compat` (a flex/grid row wrapper matching Carbon's button-group spacing), or replace the single-child `ButtonSet` here with a plain DS layout wrapper. Note this call site wraps only one `Button`, so the grouping semantics are minimal — a DS layout primitive may suffice once the surrounding panel is fully migrated. Once available, add a feature-flagged export to `src/shared/design-system-compat/index.ts` and repoint this file's remaining `@carbon/react` import.
- **Shim applied:** No
- **Closest DS component:** none shipped as a 1:1 `ButtonSet`; a DS flex/stack layout primitive is the nearest analog for the single-button grouping used here.
- **Current state in file:** import isolated to its own `@carbon/react` line with a `// FLAG:` comment; usage at the call site carries a `{/* FLAG: ... */}` marker. Import/usage left functionally untouched; the file still renders identically. All other symbols in the file (`Button`, `OverflowMenu`, `OverflowMenuItem`, `Layer`, and the three `@carbon/react/icons` — `Filter`, `SidePanelClose`, `SidePanelOpen`) were migrated to the `#/shared/design-system-compat` swap point in the same pass (2026-07-23).

> **RESOLVED 2026-08-12 — both HistoryTable FLAG entries below are now migrated.**
> Done as a full render-props to declarative rewrite in a new DS-only component,
> `HistoryTableDS.tsx`, with `HistoryTable.tsx` dispatching on
> `featureFlags.dsTasklistUI` and keeping the Carbon render-props implementation
> (renamed `HistoryTableLegacy`) byte-identical for the flag-off path. The two
> blockers recorded below were sidestepped rather than solved: no import was
> repointed, so the generic-arity tsc error never arises, and the compat
> adapter's dropped `children` is irrelevant because the DS path never uses
> render props.
>
> How the hard parts landed:
> - **Sorting** stays server-driven through the URL. The DS table runs in
>   `sorting={{manual: true, sortState, onSortingChange}}`, so it renders the
>   sort affordance and reports intent but never reorders `data` itself — no
>   conflict with the existing `search.sort` refetch. An explicit
>   `COLUMN_ID_TO_SORT_FIELD` map bridges column ids to the API's sort field
>   names (`operation`/`actor`/`date` to `operationType`/`actorId`/`timestamp`).
> - **The custom cells** became `ColumnDef.cell` renderers: `details` returns its
>   ReactNode directly (so the ASSIGN case keeps its "Assignee" label) instead of
>   being coerced to text, and `actions` renders the info `Link`. `details` and
>   `actions` carry `enableSorting: false`.
> - **`ColumnHeader.tsx` is now used only by the legacy path** — the DS table
>   renders its own sort headers.
>
> Verified against the running app: table renders with zero `cds--*` nodes,
> sorting round-trips through the URL (`operationType+asc` then `+desc`) and the
> rows genuinely reorder via refetch, `aria-sort` tracks the active column, the
> per-row info links carry the correct `auditLogKey` and preserve the sort param,
> and the details modal opens. Carbon 3001 confirmed still rendering its original
> `cds--data-table`.
>
> Not carried over: the DS `DataTable` in 0.32.1 has no `rowHref`, so the info
> link stays a cell renderer rather than a primary row action. Fine here, but
> worth knowing if row-level navigation is ever wanted.

### webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details-history/components/HistoryTable.tsx:10-20,128-192 — DataTable (render-props) + TableContainer, Table, TableBody, TableCell, TableHead, TableRow

- **Tier:** FLAG (escalated — the 2026-07-23 pre-flight bucketed `DataTable`/`TableContainer` as SHIM and only `DataTableHeader` as FLAG; that is wrong for this file's actual usage, see below).
- **Reason:** This component is built entirely on Carbon's `DataTable` **render-props children** API — `<DataTable<RowData, RowCellValues> rows headers isSortable>{({rows, headers, getTableProps, getRowProps}) => (...)}</DataTable>` — and the render function is what wires up `getTableProps()`, `getRowProps()`, the per-cell model (`cell.info.header`, `cell.value`, `cell.id`), the custom `details`/assignee cell, and the `actions` info-link cell. The shipped `carbon-compat` `DataTable` adapter (`@camunda/design-system/dist/carbon-compat/data-table.js`) **cannot back this usage**, for two independent reasons confirmed by reading the adapter source:
  1. **Generic arity mismatch (hard tsc error).** Carbon's `DataTableProps<RowType, ColTypes extends any[]>` takes two type params; the compat adapter's `DataTable<TData extends DataTableRow>` takes one. The call site `DataTable<RowData, RowCellValues>` would raise `TS2558: Expected 1 type argument, but got 2` the instant the import is repointed.
  2. **Render-props not supported (silent runtime regression).** The adapter destructures `children`, fires a dev-only warning, and never calls the render function — it renders its own TanStack `DataTable` from `columns`/`data` derived only from `rows`/`headers`. Repointing would drop the entire custom render: the `ColumnHeader` sort headers, the assignee `details` cell, and the `Information`-link `actions` cell all vanish; the `actions` column would render the raw `auditLogKey` string (its `RowData.actions` value); and DS-internal sorting would conflict with the existing URL-driven sort in `ColumnHeader`. Because the `dsTasklistUI` flag defaults off, a snapshot-based visual gate taken with the flag off would **not** catch this — the regression only appears when the flag flips on.
- **Why the dependent Table family is also FLAG:** `TableContainer`, `Table`, `TableBody`, `TableCell`, `TableHead`, `TableRow` here are all rendered *inside* the Carbon `DataTable` render-props subtree and consume Carbon's `getTableProps()`/`getRowProps()`/cell context. The compat versions are shadcn-backed and drop those Carbon props (`warnDroppedProps`), so they cannot be swapped independently while the parent `DataTable` stays Carbon — mixing a Carbon `DataTable` render host with shadcn table primitives produces incoherent markup/styling. They move as one unit with `DataTable`.
- **Suggested approach:** requires design + engineering input — rewrite the component onto the DS declarative `DataTable` API (define `columns` with custom `cell` renderers for the assignee-`details` and info-link `actions` columns; move sorting to the adapter's `isSortable`/DS sorting props or keep the external URL-driven sort and render plain DS `Table` primitives without `DataTable`). Not a mechanical import swap. Not silently approximated per FLAG protocol.
- **Shim applied:** No.
- **Closest DS component:** `@camunda/design-system/carbon-compat` `DataTable` (declarative TanStack-based) — but only after a render-props → declarative rewrite; not a drop-in for this call site.
- **Current state in file:** the six table symbols plus `type DataTableHeader` remain imported from `@carbon/react` on their original block, now carrying a `// FLAG:` comment explaining the render-props incompatibility. Imports/usages left functionally untouched; the file renders identically with the flag on or off.

### webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details-history/components/HistoryTable.tsx:19 — DataTableHeader (type-only import)

- **Tier:** FLAG (as designated by the 2026-07-23 pre-flight).
- **Reason:** Type-only import (`import { ..., type DataTableHeader } from '@carbon/react'`) used to type the `headers` memo (`useMemo<DataTableHeader[]>`). It is bound to the Carbon `DataTable` render-props data model above; the compat package does export an unrelated `DataTableHeader` interface (`{key, header}`, from `carbon-compat/data-table`), but it is a different, narrower shape and is only meaningful with the declarative adapter — swapping the type without the render-props → declarative rewrite would be a false migration. Left with the FLAG'd table cluster it belongs to. Not silently approximated per FLAG protocol.
- **Suggested approach:** resolve together with the `DataTable` rewrite above — the header typing follows whichever data model the rewritten table adopts.
- **Shim applied:** No.
- **Closest DS component:** `DataTableHeader` type from `@camunda/design-system/carbon-compat` (only valid once the component moves to the declarative adapter).

### Migrated in the same 2026-07-23 pass (context for the FLAG entries above)

HOT hold on `HistoryTable.tsx` was lifted (investigation confirmed the legacy-to-unified-webapp migration underlying this file is code-complete — the last commit, "refactor: turn history details button into link", is targeted cleanup, not open construction). One symbol was safely migrated: `Information` (`@carbon/react/icons`) → `InformationIcon` / Lucide `Info` (REMAP, new feature-flagged export added to `src/shared/design-system-compat/index.ts`; `Info` is the direct "i-in-circle" equivalent — same glyph, no approximation). This icon is independent of the table render-props context (it renders inside the `actions`-cell `Link`), so it was migrated per the icon-priority rule even though its Carbon container stays FLAG. `tsc -p tsconfig.browser.json` is clean for both touched files (the 4 pre-existing errors in the concurrently-worked `custom-filters/FieldsModal.tsx` — a `DatePicker` `locale` type mismatch — are unrelated to this migration).

---

# DS feedback to report

Not code fixes — for the Product Builder to raise with the Design System team.
Kept at the bottom of this file deliberately, since everything above is an
append-only scan log.

- **2026-08-12 — no supported way to put a trailing action on a menu or select
  row.** Wanted: each custom filter in the Tasklist filter picker showing a
  hover-revealed "..." button with Edit/Delete, matching the overflow menu the
  sidebar already renders on the same filters. There is no DS pattern for it and
  both Radix routes fail, verified against the running app:
  - **Inside `Select`**: an actions trigger placed in an open `SelectContent`
    closed the Select on pointerdown, never opened its own menu, and leaked
    `pointer-events: none` onto `<body>`. `stopPropagation` on
    pointerdown/click does not help — the Select's dismissable layer closes as
    focus moves into the nested layer. `SelectItem` also wraps children in
    Radix's `ItemText`, whose content is cloned into the trigger when selected,
    so a button placed there would render inside the closed trigger too.
  - **Inside `DropdownMenu`**: pairing a `DropdownMenuRadioItem` with a
    `DropdownMenuSub` inside a wrapper div broke the menu's item collection —
    moving to the sub-trigger dismissed the entire menu and leaked the body lock
    again. Radix menus expect items to be collection-managed descendants, which
    rules out a row shaped as "one option plus one trailing control".
  - **Shipped instead**: the picker is a `DropdownMenu` with a checked radio
    group, a separator, a `+ New filter` item and a "Custom filters" group label.
    Editing and deleting stay in the sidebar's overflow menu.
  - **Ask**: a supported row-with-trailing-action pattern for menus and selects
    — e.g. an `actions` / `trailingElement` slot on the item that renders the
    control as a sibling inside the collection, the way `AppSidebar` items
    already accept `trailingElement`. Related to the `AppSidebar` nested-button
    item below: both come from items having no place to put a second control.

- **2026-08-13 — `carbon-compat/structured-list.tsx` is a bare passthrough, but the
  analyzer scores it as a clean SWAP.** The adapter is
  `// MIGRATION TODO: no shadcn equivalent yet` re-exporting all six
  StructuredList symbols straight from `@carbon/react`. `analyze.sh` nonetheless
  buckets them SWAP and scored Tasklist's `VariableEditor.tsx` at **0.956 —
  "haiku, drop-in adapter, import path only"**. Following that score would have
  produced a migration that changes nothing: the table stays Carbon-rendered with
  the flag on, while reporting as migrated.
  - **Impact**: the score actively misleads. SHIM is weighted 0.5 in the formula
    precisely because a paper move isn't a real migration, but these passthroughs
    are classified SWAP (weight 1.0), so a file made entirely of them looks like
    the easiest possible migration.
  - **Done instead**: full rewrite onto the DS `Table` primitives in a DS-only
    `VariableEditorDS.tsx`, flag-dispatched from `VariableEditor.tsx`.
  - **Ask**: bucket passthrough adapters as SHIM (or a distinct PASSTHROUGH tier)
    rather than SWAP, so the score reflects that no surface actually moves. Same
    root issue as the `actionable-notification` entry below — that one at least
    carries a TODO marker the agent can read; the scoring layer ignores it.

- **2026-08-13 — `Modal` silently drops `preventCloseOnClickOutside`, which is a
  data-loss risk, not just a styling gap.** Tasklist's JSON variable editor opens a
  Monaco editor in a `<ComposedModal preventCloseOnClickOutside size="lg">`. The
  compat adapter drops the prop (`warnDroppedProps`), so with the flag on a stray
  backdrop click closes the dialog and **discards unsaved JSON edits**. Nothing
  warns the user; the edit is simply gone.
  - **App-side workaround**: pass Radix's `onInteractOutside` through the adapter's
    rest-props spread and `preventDefault()` it. Works because the adapter forwards
    unknown props to `DialogContent`, but it is a Radix-level escape hatch reaching
    around the Carbon-shaped API, and it only type-checks behind a cast.
  - **Ask**: honour `preventCloseOnClickOutside`. Dropping a prop that changes
    styling is survivable; dropping one that guards unsaved work is not. If it
    cannot be honoured, it should warn loudly rather than through a dev-only console
    message.
  - Related, same call site: `size="lg"` is also dropped (already logged below), so
    the dialog fell back to the narrow default — unusable for a code editor. Worked
    around with an explicit `max-width` override.

- **2026-08-13 — icon gap: Carbon `Maximize` has no row in
  `carbon-icons-to-lucide.md`.** Used in Tasklist's variables table for "expand this
  value into the JSON editor". Mapped to lucide `Maximize2` (opposing diagonal
  arrows), which is the glyph Carbon actually renders here. Note lucide also ships
  `Maximize` (four corner brackets) — a different glyph, and the wrong match despite
  the identical name, which is exactly the sort of trap the table exists to prevent.
  - **Ask**: add `Maximize` → `Maximize2` to the Common remaps table, and consider
    flagging same-name-different-glyph pairs explicitly.

- **2026-08-12 — deleting a custom filter leaves the whole page unclickable
  (`pointer-events: none` stuck on `<body>`).** Highest-severity DS issue found
  so far: the app looks fine but accepts no input at all, with zero dialogs on
  screen. Reproduced and traced by observing `document.body.style` mutations
  through the delete flow:

  | step | body `pointer-events` | dialogs | poppers |
  |------|----------------------|---------|---------|
  | OverflowMenu opens | `none` | 0 | 1 |
  | menu closes, AlertDialog opens | cleared | 1 | 0 |
  | AlertDialog closes | `none` | 0 | 0 |

  The third step re-locks `<body>` and nothing ever restores it. `<Modal danger>`
  renders a Radix `AlertDialog`, which locks the body while a layer is open and
  restores it once the close sequence settles. Deleting a filter unmounts the
  filter row — and the `OverflowMenu` that launched the dialog — in the same
  commit that closes the dialog, so Radix's layer count never returns to zero
  and the restore never fires. Needs the deleted filter to be the active one
  (that pulls a `navigate()` into the same commit), which is why it reads as
  intermittent.
  - **Likely root cause, see the next item**: `AppSidebar` forces consumers to
    nest a `<button>` inside its own `<button>`. That invalid DOM is a very
    plausible reason the dismissable-layer bookkeeping loses track.
  - **App-side workaround**: `CollapsiblePanel.tsx` now clears the leaked inline
    style on the next frame after a delete, but only once no Radix layer remains
    (so a legitimately open layer keeps its lock). Flag-gated — Carbon's Modal
    has no body lock and its path is untouched.
  - **Ask**: fix the layer accounting so closing a dialog whose trigger unmounted
    in the same commit still restores `pointer-events`. A stuck body lock takes
    the entire application down, so this deserves priority over the styling items
    in this list.

- **2026-08-12 — `AppSidebar` renders nav items as `<button>` with no slot for
  trailing actions, forcing invalid nested buttons.** `NavItemRow` wraps each
  item in `<button data-slot="app-sidebar-item">`. Tasklist's custom filters need
  a per-row overflow menu, whose trigger is itself a `<button>`, so the result is
  a button nested inside a button. React logs it as an error on every render:
  `In HTML, <button> cannot be a descendant of <button>. This will cause a
  hydration error.` Confirmed absent from the Carbon UI (zero `button button`
  matches on 3001), so the migration introduced it.
  - **Consequences**: invalid HTML, a hydration-error risk, confused assistive
    tech (nested controls), and probably the pointer-lock leak above.
  - **Ask**: give `AppSidebar` items a dedicated trailing-action/`actions` slot
    rendered as a sibling of the row control rather than a descendant, so
    per-item menus don't have to nest interactive elements. Alternatively let the
    row render as a non-button element when it carries actions.

- **2026-08-12 — `Select` trigger has no light-mode hover state, which costs
  affordance.** Checked the DS source directly
  (`src/components/ui/select.tsx:45`, the `SelectTrigger` cva base): the only
  hover rule on the trigger is `dark:hover:bg-neutral-background-medium` —
  dark mode only. In light mode, hovering the trigger changes nothing: no
  background shift, and no `cursor-pointer` either (the base sets
  `select-none` and only `disabled:cursor-not-allowed`). Nothing signals the
  control is interactive until the user clicks it. This gets worse the less
  input-like the trigger looks, so it compounds with the ghost-variant request
  below.
  - **Ask**: add a light-mode hover treatment to the `SelectTrigger` base
    (background and/or border shift, matching the token pair already used for
    the dark case) plus `cursor-pointer`, so interactivity is legible in both
    themes and in any future borderless variant.

- **2026-08-12 — request: a borderless "ghost" variant for `Select`.** The DS
  `Select` trigger only ships the bordered, filled input treatment
  (`border border-input bg-input-background`). Tasklist's filter header wants
  the select to read as a heading the user can click, not as a form field — no
  border, no fill, hover-only affordance. There is no variant prop for this, so
  it has to be recreated per consumer with `!important` overrides against
  DS-generated classes, which is brittle and will drift between surfaces.
  - **App-side state**: a local `GhostSelect` wrapper exists with a
    `GhostSelect.module.scss` overriding `background`/`border`/`padding` via
    `!important`, but the class is currently *not* applied — the wrapper is a
    plain passthrough to the DS `Select`, because the override approach kept
    breaking spacing. The filter header ships with the default bordered
    treatment for now. This is the gap, not a solved workaround.
  - **Ask**: add a `ghost` (or `variant="borderless"`) option to the `Select`
    trigger, matching how `Button` already exposes `variant="ghost"`, so this is
    a prop rather than a per-consumer CSS override.

- **2026-08-12 — `Modal` should cap its own height by default and scroll only
  its body.** Overflowing modal content pushed the modal past the viewport
  height instead of scrolling inside it. Root cause: `ModalHeader`, `ModalBody`,
  and `ModalFooter` are plain stacked divs in `carbon-compat` with no flex
  layout of their own, so the DS Dialog's `max-h-[calc(100dvh-2rem)]` scrolls
  the whole header+body+footer block together rather than confining the scroll
  to the growing body (hit this when adding task variables in `FieldsModal`).
  - **App-side workaround**: made `.modal` a flex column, pinned
    `.modalHeader`/`.modalFooter`, gave `.modalBody` the overflow, and set an
    explicit `max-height: 85vh` on `.modal` alongside the DS's own
    `calc(100dvh-2rem)`.
  - **Ask**: make this the Modal's default — height cap plus body-only scroll,
    so consumers don't each re-derive the flex layout. Related gap in the same
    component: `carbon-compat/modal.tsx` drops the `size` prop entirely
    (confirmed via `warnDroppedProps`), so there is no size scale at all and
    every wider modal needs a manual `className` width override.

- **2026-08-12 — `carbon-compat/select.tsx` renders an empty `<label>` for
  `labelText=""`, making a label-less Select 4px taller than its own trigger.**
  The wrapper is always `<div className="flex flex-col gap-1">` and the label
  renders whenever `labelText !== undefined` — so `labelText=""` produces an
  empty label element plus the unconditional `gap-1` (4px). Result: a Select
  whose trigger is 36px reports 40px to its parent, so it can never be
  vertically centered against a sibling 36px icon button (hit this in
  Tasklist's `Filters` panel header — the select and the sort button would not
  line up, and the section's padding read as uneven top/bottom vs. left/right).
  Nothing app-side fixes it cleanly: CSS overrides on `label` and on the
  wrapper's `gap` were both ineffective because the wrapper class is generated
  inside the DS package, not exposed for styling.
  - **App-side fix that does work**: pass `hideLabel` and drop `labelText`
    entirely, so the label branch never renders. Applied in `Filters.tsx`.
  - **Two asks for the DS team**: (1) don't render the label element for an
    empty-string `labelText`, and/or drop `gap-1` when no label renders;
    (2) `hideLabel` is listed in `warnDroppedProps("Select", ...)` even though
    the component *does* honor it (`hideLabel && "sr-only"` on the label) — the
    warning is wrong and steers callers away from the one prop that works.
  - Related: `carbon-compat/dropdown.tsx` destructures `hideLabel` and then
    genuinely ignores it (found in the 2026-07-25 pass, above). `Select` and
    `Dropdown` disagree on the same prop.

- **2026-07-27 — `AppSidebar` collapsed-rail icon buttons are too compressed**,
  both in the DS's own Storybook ("the library") and here in Tasklist's
  migrated `CollapsiblePanel`. Should be square buttons, matching the pattern
  in camunda-hub/frontend's own sidebar ("the hub"). Root cause found and
  worked around app-side but still worth reporting: the DS component itself
  (`src/components/ui/app-sidebar.tsx`'s `navButton` cva, `expanded: false`
  case) sizes buttons to `min-h-10` (40px) with `w-full` width, but its own
  documented default `collapsedWidth` ("3rem" per `AppSidebarProps`) doesn't
  leave enough room once the inner content div's own `px-2` (16px) padding is
  subtracted — 3rem (48px) - 16px = 32px width vs. 40px height, still not
  square even at the DS's own suggested default. Worth the DS team either
  correcting the default `collapsedWidth` or accounting for the inner
  padding in the button's own sizing.
  - **App-side workaround applied**: `CollapsiblePanel.tsx` passes
    `collapsedWidth="3.5rem"` (56px) instead of relying on the default, so
    56px - 16px padding = 40px width, matching the 40px `min-h-10` height.

- **2026-07-22 — `carbon-compat/actionable-notification.tsx` is not implemented,
  but the docs claim it is.** The shipped adapter is a bare passthrough
  re-export of `@carbon/react`'s `ActionableNotification`, explicitly marked
  `// MIGRATION TODO: adapter pending Alert shadcn component`. Both
  `mapping.json` and `carbon-migration-tiers.md` list it as REMAP-ready, which
  the shipped code does not back up.
  - **Impact**: `TurnOnNotificationPermission.tsx` cannot be migrated at all
    until the real adapter ships — this is a hard blocker, not a deferral.
  - **Ask**: either ship the adapter or correct the tier docs so consumers
    don't plan around a component that isn't there.
