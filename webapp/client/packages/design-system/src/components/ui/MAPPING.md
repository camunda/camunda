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

## Stats

**43 Carbon wrappers** total — **11 direct**, **19 conceptual**, **13 Camunda-built** (no shadcn upstream). **43 done**, **0 to go** — every wrapper now has a `*.shadcn.tsx` implementation, story, and migration guide.
