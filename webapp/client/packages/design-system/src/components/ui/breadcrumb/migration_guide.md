# Breadcrumb — Carbon → shadcn migration guide

## Structural differences

Carbon uses an implicit nested structure: a single `Breadcrumb` wraps `BreadcrumbItem`s, and item state (current, separator) is controlled by props.

shadcn is explicit and decomposed: `Breadcrumb` is a semantic `<nav>`, you must add an inner `BreadcrumbList` (`<ol>`), every item between others needs an explicit `BreadcrumbSeparator`, and you choose between `BreadcrumbLink` (clickable), `BreadcrumbPage` (current/non-clickable), or `BreadcrumbEllipsis` (truncation marker) per item.

| Concept | Carbon | shadcn |
|---|---|---|
| Wrapper | `<Breadcrumb>` | `<Breadcrumb>` + `<BreadcrumbList>` |
| Item | `<BreadcrumbItem href="…">Label</BreadcrumbItem>` | `<BreadcrumbItem><BreadcrumbLink href="…">Label</BreadcrumbLink></BreadcrumbItem>` |
| Current page | `<BreadcrumbItem isCurrentPage>` | `<BreadcrumbItem><BreadcrumbPage>` |
| Separator | implicit (`/` rendered automatically) | explicit `<BreadcrumbSeparator />` between items |
| Truncation | manual (`<BreadcrumbItem>…</BreadcrumbItem>`) | `<BreadcrumbEllipsis />` (with `MoreHorizontal` icon) |
| Custom separator | `noTrailingSlash` only (boolean) | full override via `BreadcrumbSeparator` children |

## Carbon features missing in shadcn

- **`noTrailingSlash` prop** — Carbon supports hiding the trailing separator; shadcn has no separator after the last item by design (you simply don't render one).
- **Skeleton state** — Carbon provides `BreadcrumbSkeleton`; shadcn has no built-in skeleton. Use the generic `skeleton` shadcn primitive.
- **Overflow menu integration** — Carbon's `OverflowMenu` can be embedded as an item out-of-the-box. shadcn requires composing `DropdownMenu` inside `BreadcrumbItem` manually (the example shows this pattern).
- **Built-in `href` resolution** — Carbon's `BreadcrumbItem` accepts `href` directly; shadcn forces you to wrap with `BreadcrumbLink` (or use `asChild` to plug a router link).

## shadcn features missing in Carbon

- **`asChild` slot pattern** — `BreadcrumbLink asChild` lets you compose with router libraries (`next/link`, `react-router`) without wrapper anchors. Carbon's items are not slot-based.
- **Explicit semantic split** — `BreadcrumbPage` carries `aria-current="page"` automatically and is announced as a non-clickable landmark; Carbon mixes both behaviours into one component.
- **Custom separators per item** — shadcn allows different separators between different items by passing children to `BreadcrumbSeparator`. Carbon's separator is global.

## Styling

- Carbon: token-driven (`spacing-03`, `text-secondary`, etc.), uses `<a>` underline + hover color.
- shadcn: utility classes via Tailwind, uses `text-muted-foreground` with `hover:text-foreground` transition. No underline by default — add `underline-offset-4 hover:underline` if needed to mimic Carbon.

## Migration checklist

1. Wrap children in a `<BreadcrumbList>`.
2. Replace each `<BreadcrumbItem href>` with `<BreadcrumbItem><BreadcrumbLink href>`.
3. Replace `isCurrentPage` items with `<BreadcrumbPage>`.
4. Insert `<BreadcrumbSeparator />` between every consecutive pair of items.
5. Replace manual `…` items with `<BreadcrumbEllipsis />`.
6. If using a router, prefer `<BreadcrumbLink asChild><RouterLink to="…">…</RouterLink></BreadcrumbLink>`.
