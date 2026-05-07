<!--
Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
one or more contributor license agreements. See the NOTICE file distributed
with this work for additional information regarding copyright ownership.
Licensed under the Camunda License 1.0. You may not use this file
except in compliance with the Camunda License 1.0.
-->

# Carbon to shadcn Component Mapping

## Overview

This file is the single source-of-truth for the Carbon-to-shadcn migration in the `@camunda/design-system` package. It lists every Carbon component wrapper currently living under `src/components/ui/`, its closest shadcn/ui equivalent (or "none"), and the migration status. The set of wrappers was determined by mining `@carbon/react` imports across the `tasklist/` and `operate/` webapps, so this list reflects the actual Carbon surface area used by Camunda's frontends.

## Status legend

- `not started` — wrapper folder exists but no shadcn implementation has been written yet
- `in progress` — shadcn implementation is partially in place (e.g. component exists but story/guide/types are pending)
- `done` — shadcn implementation, story, and migration guide are all in place
- `n/a` — no shadcn equivalent exists; row is informational only

## Mode switching (single control point)

The package has two modes:

- **shadcn** (default) — `src/index.ts` re-exports from `src/index.shadcn.ts`. Components resolve to shadcn-backed adapters; CSS auto-loaded as side effects: `theme.scss` (Carbon design tokens + IBM Plex fonts) + `globals.css` (shadcn neutral palette + Tailwind layer setup).
- **carbon** — flip `src/index.ts` to re-export from `src/index.carbon.ts`. Components resolve to bare `@carbon/react` re-exports; CSS auto-loaded: `carbon.scss` (Carbon's per-component rules; pulls in `theme.scss` transitively).

**One file controls both** (JS + CSS): `src/index.ts`. Consumers never import stylesheets manually — `import {Button} from '@camunda/design-system'` is enough. The raw stylesheet exports (`@camunda/design-system/{theme,carbon}.scss`, `@camunda/design-system/globals.css`) remain in `package.json` `exports` for tools like Storybook that need both modes loaded simultaneously, but app code shouldn't reach for them.

**Theme tokens always load** regardless of mode. `theme.scss` is included by both `index.shadcn.ts` (directly) and `index.carbon.ts` (via `carbon.scss`'s `@use './theme'`). This is intentional — Operate's and Tasklist's styled-components reference `var(--cds-spacing-*)` / `var(--cds-color-*)` extensively. Migrating those callsites off `--cds-*` is a separate workstream; until it lands, both modes need the tokens.

## Adapter convention

Every wrapper folder ships three files: `<name>.carbon.tsx` (bare Carbon re-export), `<name>.shadcn.tsx` (the shadcn primitive), and `<name>.adapter.tsx` (the public surface, exported from `index.shadcn.ts`).

The adapter's job is to translate Carbon-shaped props into shadcn-shaped props so consumer call-sites compile unchanged. Examples: `Tag` adapter maps `type='red'` → `<Badge variant='destructive'>`; `Button` adapter maps `kind='primary'` → `variant='default'`; `Modal` adapter branches on `danger` to render `AlertDialog` instead of `Dialog`. Lossy props (Carbon-only flags with no shadcn analogue) are dropped with a dev-only `warnDroppedProps` warning.

**Exception — passthrough adapters.** When the shadcn primitive is intentionally built to mirror Carbon's exact API (same prop names, same render-prop contract), the adapter has nothing to translate and becomes a one-line re-export. `data-table` is the only wrapper that takes this route, and it does so deliberately:

- Carbon's `DataTable` is a render-prop API (`<DataTable rows={...} headers={...}>{({getHeaderProps, getRowProps, ...}) => ...}</DataTable>`) used pervasively by the webapps. Translating to a different shape would force every call-site to rewrite its render function.
- The shadcn `data-table.shadcn.tsx` therefore reproduces Carbon's prop API (`rows`, `headers`, `isSortable`, `radio`, helper-prop shapes) under a Tailwind-driven implementation.
- Trade-off: the "shadcn primitive" looks Carbon-shaped, so we are locked into that surface until/unless we add a more idiomatic `<DataTable.Header>`-style API behind the same identifier — at which point the adapter file gains real translation logic.
- The adapter file stays as a re-export so the folder structure remains uniform across every wrapper.

If you find yourself reaching for another passthrough adapter, prefer designing the shadcn primitive on its own terms and putting translation logic in the adapter — that's how the other 30 wrappers are structured.

## Mapping table

### Direct matches (Carbon name == shadcn name)

| Wrapper | Carbon component(s) | shadcn equivalent | Match type | Status | Notes |
|---|---|---|---|---|---|
| breadcrumb | Breadcrumb, BreadcrumbItem | breadcrumb | direct | done | Carbon=monolithic, shadcn=composed list with explicit separators |
| button | Button | button | direct | done | Carbon `kind` != shadcn `variant`; shadcn missing `xl`/`2xl`/`danger--tertiary`/`danger--ghost` |
| checkbox | Checkbox | checkbox | direct | done | Carbon=monolithic field, shadcn=headless box (no built-in label) |
| combo-box | ComboBox | combobox | direct | done | shadcn version uses Base UI (`@base-ui/react`), not Radix; depends on `input-group` |
| pagination | Pagination | pagination | direct | done | Big scope mismatch: Carbon=stateful data-table footer, shadcn=presentational link list |
| popover | Popover, PopoverContent | popover | direct | done | Carbon `align` token splits into shadcn `side` + `align` |
| select | Select, SelectItem | select | direct | done | Carbon=native `<select>`, shadcn=Radix custom listbox; trade-off: native form/mobile vs. custom panel |
| table | Table, TableBody, TableCell, TableHead, TableHeader, TableRow, TableBatchAction | table | direct | done | NAMING CLASH: Carbon `TableHead` = `<thead>`, shadcn `TableHead` = `<th>` (flipped) |
| tabs | Tab, TabList, TabPanel, TabPanels, Tabs | tabs | direct | done | Pair-by-index (Carbon) vs. pair-by-`value` (shadcn) |
| text-area | TextArea | textarea | direct | done | Carbon=monolithic field, shadcn=raw `<textarea>`; rename `TextArea`→`Textarea` |
| tooltip | Tooltip | tooltip | direct | done | Carbon: per-tooltip delays; shadcn: `<TooltipProvider>` centralises delays |

### Conceptual matches (different names, similar role)

| Wrapper | Carbon component(s) | shadcn equivalent | Match type | Status | Notes |
|---|---|---|---|---|---|
| actionable-notification | ActionableNotification | alert + alert-dialog | conceptual | done | No 1-to-1: inline=`Alert`+`Button`, modal=`AlertDialog`; only 2 status variants in shadcn (default/destructive) |
| callout | Callout | alert | conceptual | done | Re-exports `Alert` from canonical primitive; info/warning kinds need custom Tailwind |
| date-picker | DatePicker, DatePickerInput | calendar | conceptual | done | shadcn date-picker is composition-only; `Popover`+`Calendar`; loses typeable input by default |
| dropdown | Dropdown | select | conceptual | done | Re-exports `Select`; warning: shadcn `DropdownMenu` is for action-menus (`MenuButton`/`OverflowMenu`) |
| icon-button | IconButton | button | conceptual | done | Compose `Button size="icon"` + `Tooltip` + manual `aria-label`; `isSelected` → use `Toggle` primitive |
| inline-loading | InlineLoading | spinner | conceptual | done | shadcn ships only the spinner; status text + finished/error icons are app composition |
| inline-notification | InlineNotification | alert | conceptual | done | Re-exports `Alert`; dismissibility, status icons, success/warning kinds are app composition |
| loading | Loading | spinner | conceptual | done | Re-exports `Spinner`; `withOverlay` and centering are app composition |
| menu-button | MenuButton, MenuItem | dropdown-menu | conceptual | done | Trigger composes `Button` + chevron manually; not Carbon Dropdown |
| modal | Modal, ComposedModal, ModalHeader, ModalBody, ModalFooter | dialog | conceptual | done | Re-exports `Dialog`; destructive (`danger`) modals should use `AlertDialog` instead |
| overflow-menu | OverflowMenu, OverflowMenuItem | dropdown-menu | conceptual | done | Same `DropdownMenu` as menu-button; trigger is icon `Button` + `MoreVertical` |
| skeleton-icon | SkeletonIcon | skeleton | conceptual | done | shadcn ships one generic `Skeleton`; size via Tailwind (`size-4`); animation differs (pulse vs. shimmer) |
| skeleton-text | SkeletonText | skeleton | conceptual | done | shadcn ships one generic `Skeleton`; multi-line paragraphs are app composition |
| structured-list | StructuredListWrapper, StructuredListBody, StructuredListCell, StructuredListHead, StructuredListInput, StructuredListRow, StructuredListSkeleton | table | conceptual | done | Re-exports `Table`; radio-row variant requires `radio-group` primitive |
| tag | Tag | badge | conceptual | done | Carbon ships 12 colour types vs. shadcn's 6 variants; `filter`/`onClose` is manual composition |
| text-input | TextInput | input | conceptual | done | Carbon=monolithic field; shadcn=raw `<input>`; password/number/search are Carbon-only sub-components |
| tile | Tile | card | conceptual | done | Carbon's 5 tile variants (Tile/Clickable/Selectable/Radio/Expandable) all collapse into Card composition |
| toast-notification | ToastNotification | sonner | conceptual | done | Paradigm shift: declarative `<Toast>` JSX → imperative `toast(...)` + global `<Toaster />` |
| toggle | Toggle | switch | conceptual | done | Carbon Toggle = shadcn Switch; do NOT use shadcn `toggle` (that's a pressable button) |

### Camunda-built primitives (no upstream shadcn equivalent — recreated in plain HTML/Tailwind)

These wrappers have no shadcn/ui upstream counterpart. Each one is implemented directly inside this package on top of Tailwind, Radix Slot, and existing local primitives (Popover, Checkbox, Button) — no new dependencies. Export names match Carbon so consumers can swap `'@camunda/design-system/carbon'` ↔ `'@camunda/design-system/shadcn'` with minimal call-site changes.

| Wrapper | Carbon component(s) | shadcn approach | Match type | Status | Notes |
|---|---|---|---|---|---|
| code-snippet | CodeSnippet | custom — `<pre><code>` + Button + clipboard API | local | done | inline / single / multi types; expand-collapse for multi; drops Carbon's tooltip-on-button |
| column | Column | custom — CSS grid + scoped `<style>` for breakpoints | local | done | `xlg` renamed to `xl`; percentage spans dropped |
| contained-list | ContainedList, ContainedListItem | custom — `<section>` + `<ul>` + Tailwind | local | done | on-page / disclosed kinds; sm/md/lg/xl sizes |
| grid | Grid | custom — CSS grid via inline `gridTemplateColumns` | local | done | 16-col by default; `condensed`/`narrow` gutter modes |
| header | Header | custom — sticky `<header>` + slot helpers | local | done | Subset of UIShell (Header / Name / Navigation / MenuItem / GlobalBar / GlobalAction) |
| heading | Heading | custom — context-aware `<h1>` … `<h6>` | local | done | Pairs with Section; reads `useSectionLevel()` |
| layer | Layer | custom — context with `data-layer-level` + bg tones | local | done | Three levels (0/1/2); `withBackground` swaps `bg-background` / `bg-card` / `bg-muted` |
| link | Link | custom — styled `<a>` (cva) + Radix Slot | local | done | `asChild` instead of polymorphic `as`; `disabled` keeps `<a>` (Carbon swaps to `<p>`) |
| multi-select | MultiSelect | composed — Popover + Checkbox | local | done | Drops Downshift-only features (selectionFeedback, compareItems, downshiftProps); covers the Camunda surface |
| section | Section | custom — context provider + polymorphic `<section>` | local | done | Pairs with Heading; auto-increments level (clamped at 6) |
| stack | Stack | custom — flex utility wrapper | local | done | `gap` accepts numeric step (1–10) or CSS string |
| tree-view | TreeView, TreeNode | custom — `<ul role="tree">` + context + keyboard nav | local | done | Up/Down/Left/Right/Enter/Space; controlled or uncontrolled active/selected/expanded |
| unordered-list | UnorderedList, ListItem | custom — `<ul>` + `<li>` with Tailwind list utilities | local | done | `nested` and `isExpressive` props preserved |

## Infrastructure (shadcn-only)

Primitives with no Carbon counterpart, pulled in as dependencies of the wrappers above:

| Wrapper | shadcn | Pulled in for | Status |
|---|---|---|---|
| alert | alert | actionable-notification, callout, inline-notification | placed (canonical primitive) |
| alert-dialog | alert-dialog | actionable-notification (modal variant) | placed (canonical primitive) |
| badge | badge | tag | placed (canonical primitive) |
| calendar | calendar | date-picker | placed (canonical primitive) |
| card | card | tile | placed (canonical primitive) |
| dialog | dialog | modal | placed (canonical primitive) |
| dropdown-menu | dropdown-menu | menu-button, overflow-menu | placed (canonical primitive) |
| input-group | input-group | combo-box | placed (no story/guide) |
| skeleton | skeleton | skeleton-icon, skeleton-text | placed (canonical primitive) |
| sonner | sonner | toast-notification | placed (canonical primitive; theme syncs via MutationObserver, not next-themes) |
| spinner | spinner | inline-loading, loading | placed (canonical primitive) |
| switch | switch | toggle | placed (canonical primitive; NOT shadcn `toggle` — that's a different primitive) |

### Carbon fallbacks (migration complete)

Originally re-exported from `@carbon/react` directly via `src/index.shadcn.ts`. All 9 wrappers now have shadcn-backed adapters and the design-system is single-stylesheet for consumers that don't import `@carbon/react` UI components directly. Operate's `index.scss` no longer imports `@camunda/design-system/carbon.scss`. Tasklist still imports it because `tasklist/client/src/` continues to use `Button`/`Heading`/`Stack`/`OverflowMenu`/`TextInput`/`Modal*`/`Select`/`Popover` directly from `@carbon/react`; migrating those call-sites to design-system identifiers is a separate workstream.

| Wrapper | Carbon component(s) | shadcn approach | Match type | Status | Notes |
|---|---|---|---|---|---|
| data-table | DataTable | composed — render-prop wrapper around shadcn `Table` + state hooks for sort/expand/select/batch | local | done | Render-prop primitive + all sub-components shipped. **Passthrough adapter** — see "Adapter convention" above; the shadcn primitive deliberately mirrors Carbon's render-prop API so call-sites compile unchanged. |
| data-table-skeleton | DataTableSkeleton | composed — `Skeleton` rows in shadcn `Table` shell | local | done | Honours `columnCount`/`rowCount`/`headers`/`showHeader`/`showToolbar`/`zebra`; row height keyed off `size` (xs/sm/md/lg/xl) |
| table-container | TableContainer | custom — flex column with `title`/`description` header slot | local | done | `decorator` and `aiEnabled` (Carbon AI label) dropped with `warnDroppedProps`; `stickyHeader`→`sticky`, `useStaticWidth`→`fitContent` |
| table-toolbar | TableToolbar, TableToolbarContent, TableToolbarSearch, TableToolbarMenu, TableToolbarAction | custom — flex bar + composes existing primitives (`InputGroup`/`DropdownMenu`/`Button`) | local | in progress | `TableToolbar` and `TableToolbarContent` shipped; `TableToolbarSearch`/`TableToolbarMenu`/`TableToolbarAction` deferred (rare in webapps; wrap Carbon `Search`/`OverflowMenu`) |
| table-batch-actions | TableBatchActions, TableBatchAction | custom — animated overlay bar with selection count, cancel, per-action buttons | local | done | `translateWithId` honoured for selection label; `renderIcon`/`hasIconOnly` supported on actions |
| table-expand | TableExpandHeader, TableExpandRow, TableExpandedRow | custom — `<th>`/`<tr>`/`<tr>` + lucide Chevron toggle + `aria-expanded`/`aria-controls` | local | done | Carbon's deprecated `enableExpando` aliased to `enableToggle`; deprecated `ariaLabel` dropped via `warnDroppedProps` |
| table-select | TableSelectAll, TableSelectRow | composed — shadcn `Checkbox` (or native `radio`) inside `<th>`/`<td>` | local | done | Synthesises `event.target.checked` so callers reading the Carbon-shaped `MouseEvent<HTMLInputElement>` continue to work |
| password-input | PasswordInput | composed — `InputGroup` + `Input` + visibility toggle button with `Eye`/`EyeOff` icons | local | done | Carbon's labelText/helperText/invalidText rendered around the field; `enableCounter`/`tooltipPosition`/`light`/`inline` dropped via `warnDroppedProps` |
| ordered-list | OrderedList | custom — styled `<ol>` mirroring `unordered-list` | local | done | `nested`/`native`/`isExpressive` honoured via `data-` attributes and Tailwind |

## Stats

**52 Carbon wrappers** total — **11 direct**, **19 conceptual**, **13 Camunda-built**, **9 migrated Carbon fallbacks**. **51 done**, **1 in progress** (`table-toolbar` — `Search`/`Menu`/`Action` sub-parts deferred).
