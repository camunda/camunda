# Table — Carbon → shadcn migration guide

## ⚠️ Naming clash (the big one)

Carbon and shadcn use the **same names for opposite elements**. This is the most subtle and dangerous part of the migration.

| Component name | Carbon renders | shadcn renders |
|---|---|---|
| `TableHead` | `<thead>` (the head section) | `<th>` (a column header cell) |
| `TableHeader` | `<th>` (a column header cell) | `<thead>` (the head section) |

Read that twice. The names are flipped.

If you do a naive find-and-replace `import {Table, TableHead, TableHeader, …} from '@carbon/react'` → `import {…} from '@/components/ui/table/table.shadcn'`, your tables will silently render with `<thead>` cells inside `<th>` rows (or worse, depending on how strict the parser is) — broken markup, broken styling, broken accessibility, no compile error.

**Migration rule:** do the swap deliberately, by hand, paying attention to which is which. Or alias on import:

```ts
import {
  TableHead as TableHeadCell,   // shadcn TableHead = <th>
  TableHeader as TableHeadSection, // shadcn TableHeader = <thead>
} from '@/components/ui/table/table.shadcn';
```

## Element mapping (canonical)

| HTML element | Carbon name | shadcn name |
|---|---|---|
| `<table>` | `Table` | `Table` |
| `<thead>` | `TableHead` | `TableHeader` |
| `<tbody>` | `TableBody` | `TableBody` |
| `<tfoot>` | not exposed (use raw `<tfoot>`) | `TableFooter` |
| `<tr>` | `TableRow` | `TableRow` |
| `<th>` | `TableHeader` | `TableHead` |
| `<td>` | `TableCell` | `TableCell` |
| `<caption>` | not exposed (use raw `<caption>`) | `TableCaption` |

## Underlying primitive

- **Carbon `Table`** is part of the larger `DataTable` family — full-featured (sortable columns, expandable rows, batch actions, selection, sticky header, virtualization) when used through `DataTable`. The standalone `<Table>` is the unstyled-shell entry point.
- **shadcn `Table`** is a **thin presentational wrapper** around the native HTML table elements. No sorting, no selection, no virtualization. For interactive features you typically pair it with `@tanstack/react-table` (the official guidance).

## Carbon features missing in shadcn

- **`size` prop** (`xs` / `sm` / `md` / `lg` / `xl`) — Carbon offers density variants. shadcn has one default density; restyle with Tailwind classes.
- **`useZebraStyles`** — Carbon stripes alternate rows. shadcn requires `[&_tr:nth-child(even)]:bg-muted/50` or similar.
- **`stickyHeader`** — Carbon supports a sticky table header out of the box. shadcn does not — wrap `<TableHeader>` in a sticky container manually.
- **`overflowMenuOnHover`** — Carbon's row-level overflow menus appear on hover only. No shadcn equivalent.
- **`isSortable` / `onSortHeader` / sort indicator icons** — Carbon's sorting is built into `TableHeader`. shadcn requires you to add the chevron icons and click handlers, or use `@tanstack/react-table`.
- **`<TableBatchAction>` / `<TableBatchActions>`** — Carbon's row-selection toolbar (with multi-select + actions). No shadcn primitive; build with `Checkbox` + `Button` + state.
- **`<TableExpandRow>` / `<TableExpandHeader>` / `<TableExpandedRow>`** — Carbon's expandable-row primitives. No shadcn primitive; manage open state and render conditional `<tr>` underneath.
- **`<TableSelectAll>` / `<TableSelectRow>`** — Carbon's checkbox-selection primitives. shadcn requires composing checkboxes manually.
- **`<TableContainer>` + `<TableToolbar>` + `<TableToolbarContent>` + `<TableToolbarSearch>`** — full data-table chrome. Build manually in shadcn.
- **`<TableSkeleton>` / `<DataTableSkeleton>`** — Carbon ships skeleton states. shadcn has none — use the generic `skeleton` primitive for cells.
- **`getHeaderProps` / `getRowProps` / `getSelectionProps` (DataTable render-prop)** — Carbon's `DataTable` is a controlled compound component with prop-getters. shadcn has nothing comparable; use `@tanstack/react-table` for headless equivalents.

## shadcn features missing in Carbon

- **`TableCaption`** — shadcn ships a styled `<caption>` primitive. Useful for screen-reader summaries.
- **`TableFooter`** — pre-styled `<tfoot>` (muted background, font-medium). Carbon expects you to use a raw `<tfoot>`.
- **Tailwind composability** — every element accepts `className`, merged with `cn()`. Quick one-off restyling without ejecting the whole component.
- **Hover + selected state via `data-state="selected"`** — `<TableRow>` styles `hover:bg-muted/50` and `data-[state=selected]:bg-muted` out of the box. Set `data-state="selected"` on the `<tr>` to mark it.
- **Built-in horizontal overflow** — shadcn wraps `<table>` in `<div className="relative w-full overflow-auto">`. Carbon expects you to wrap manually if you need a horizontal scroller.

## Behavioural differences

- **Default font size** — Carbon: 14px (Carbon body-short). shadcn: `text-sm` (14px). Match.
- **Cell padding** — Carbon: token-driven, varies by `size`. shadcn: `p-4` (16px) for `<TableCell>`, `h-12 px-4` (48px tall, 16px horizontal) for `<TableHead>`.
- **Border colour** — Both use the theme border token; visual match depends on the active palette.
- **Row hover** — shadcn: `hover:bg-muted/50` always on. Carbon: depends on whether `DataTable` is in interactive mode.
- **Selected-row marker** — Carbon: `selected` prop / class. shadcn: `data-state="selected"` attribute on `<tr>`.

## Migration checklist

1. **First: alias-rename or careful hand-port to avoid the `TableHead`/`TableHeader` flip.** This is the single most likely source of silent breakage.
2. Replace `<TableHead>` (Carbon `<thead>`) with `<TableHeader>` (shadcn `<thead>`).
3. Replace `<TableHeader>` (Carbon `<th>`) with `<TableHead>` (shadcn `<th>`).
4. Wrap data rows in `<TableBody>` if they weren't already (Carbon's wrapper sometimes elides it).
5. If you used `useZebraStyles`: add `[&_tr:nth-child(even)]:bg-muted/50` to `<TableBody>`.
6. If you used `stickyHeader`: add `sticky top-0 bg-background z-10` to `<TableHeader>` (and ensure the parent has a scroll container).
7. If you used `size`: drop the prop and tweak `<TableHead>`/`<TableCell>` padding via `className`.
8. If you used Carbon's `DataTable` (sorting / selection / expansion / batch actions): plan a separate migration to `@tanstack/react-table` + shadcn `Table` + custom UI for the chrome. There is no drop-in.
9. If you used `<TableBatchAction>`: build it with `Checkbox` (selection) + `Button` (action) + your own toolbar layout.
10. Test screen-reader announcement: shadcn's table is bare HTML, so make sure `<TableCaption>` is set if your table needs a description, and that header `<th>` cells have `scope="col"`/`"row"` where applicable.
