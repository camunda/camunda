# Pagination — Carbon → shadcn migration guide

## They solve different problems

This is the biggest gap on the list. Carbon's `Pagination` and shadcn's `Pagination` overlap in name only.

- **Carbon `Pagination`** is a **stateful data-table footer**. Give it `totalItems`, `pageSize`, `pageSizes` and it renders: page-size selector, "1–10 of 103 items" text, page input/dropdown, and prev/next buttons. It manages all the pagination math internally.
- **shadcn `Pagination`** is a **presentational `<nav>` of links**. You pre-compute which page numbers to show and feed in `<PaginationLink>` / `<PaginationPrevious>` / `<PaginationNext>` / `<PaginationEllipsis>` yourself. There is no `totalItems`, no `pageSize`, no page-size selector.

If your app uses Carbon's `Pagination` to drive a table, swapping to shadcn's `Pagination` requires you to **own the state and derived UI** (which page is active, how many to show, when to show ellipses, and whether to render a page-size selector at all — there is no shadcn primitive for that last part).

## Structural differences

| Concept | Carbon | shadcn |
|---|---|---|
| Wrapper | `<Pagination totalItems pageSize page onChange />` (single component) | `<Pagination><PaginationContent>…</PaginationContent></Pagination>` (composed) |
| Page list | implicit (prev / page-input / next) | explicit `<PaginationItem>` per page |
| Active page | `page` prop | `<PaginationLink isActive>` per item |
| Prev / Next | implicit; `backwardText` / `forwardText` props | `<PaginationPrevious>` / `<PaginationNext>` (anchor-based) |
| Ellipsis ("…") | not modeled (Carbon uses a numeric input or dropdown to jump pages) | `<PaginationEllipsis>` |
| Page-size selector | built-in, controlled by `pageSizes` + `pageSize` + `onChange({pageSize})` | none — render your own `<Select>` |
| "1–10 of 103 items" text | built-in | none — render your own `<span>` |
| Compact mode | `pagesUnknown` (hides total) | not modeled — the entire footer is what you build |

## Carbon features missing in shadcn

- **`totalItems` / `pageSize` / `page` / `onChange({page, pageSize})`** — Carbon owns the math; shadcn owns nothing.
- **Page-size selector (`pageSizes` prop)** — built-in dropdown to switch between 10 / 20 / 50 items per page. shadcn requires composing a `<Select>` next to the pagination.
- **"X–Y of Z items" range text** — Carbon localizes and renders this. shadcn does not — render `{(page - 1) * pageSize + 1}–{Math.min(page * pageSize, total)} of {total}` yourself.
- **Page-input vs. page-dropdown** — Carbon switches between an input field (for many pages) and a dropdown (for few). shadcn has no equivalent — you decide what to show in `<PaginationContent>`.
- **`pagesUnknown` / `pageInputDisabled` / `pageSizeInputDisabled`** — feature flags for partial UIs. No shadcn equivalent.
- **Skeleton state (`PaginationSkeleton`)** — Carbon ships a skeleton variant. shadcn has none — use the generic `skeleton` primitive.
- **`isLastPage` boolean** — Carbon disables `Next` once on the last page. shadcn requires you to omit / `aria-disabled` `<PaginationNext>` yourself.
- **`backwardText` / `forwardText` / `itemsPerPageText` / `itemRangeText` (i18n hooks)** — Carbon centralises label strings. shadcn hardcodes "Previous" / "Next" / "More pages" in English; rewrite or pass children.
- **Built-in keyboard handling** — Carbon's input/dropdown handle arrow keys and Enter. shadcn delegates to plain `<a>` semantics.

## shadcn features missing in Carbon

- **Anchor-based, server-friendly** — `<PaginationLink href="?page=2">` works without JS. SEO-friendly out of the box. Carbon's pagination is JS-only.
- **`isActive` prop** — explicit current-page marker that maps to `aria-current="page"`. Cleaner semantics than Carbon's "the current page is whatever `page` is".
- **Composability** — drop `<PaginationEllipsis>` wherever you want, render only the numbers you care about, intermix custom buttons. Carbon's prev/input/next layout is fixed.
- **`asChild`-style swap** — pass router-library `<Link>` via children to integrate with Next.js / React Router; Carbon needs a wrapper component.
- **No CSS-in-JS dependency** — pure Tailwind classes via `buttonVariants`; tweak with `className`.

## Behavioural differences

- **Default element** — Carbon: `<div>` shell with form controls inside. shadcn: `<nav role="navigation" aria-label="pagination">` wrapping a `<ul>` of `<li>` anchors.
- **Layout** — Carbon: full-width footer that hugs a table. shadcn: centered horizontally (`mx-auto flex w-full justify-center`); restyle if you need left/right alignment.
- **Variant of "current" link** — shadcn renders the active page as `outline` button variant; non-active as `ghost`. Carbon highlights via background colour token.
- **Accessibility** — Both meet WCAG. shadcn's `<PaginationEllipsis>` includes a visible icon plus `<span className="sr-only">More pages</span>`; `<PaginationNext>` / `<PaginationPrevious>` carry `aria-label`s.

## Migration checklist

1. **Decide whether shadcn's primitive is enough.** If you need the Carbon-style "1–10 of 103 / items per page selector / page input" footer, you'll need to build it yourself — shadcn only ships the link list. Often a `Pagination` + a sibling `<Select>` + a `<span>` for the range text gets you there.
2. Compute the page-number list in your component: which numbers to show, where to insert ellipses (a common pattern: `[1, …, page-1, page, page+1, …, lastPage]`).
3. Replace `<Pagination totalItems pageSize page onChange />` with `<Pagination><PaginationContent>…</PaginationContent></Pagination>`.
4. For each page number render `<PaginationItem><PaginationLink href={href(n)} isActive={n === page}>{n}</PaginationLink></PaginationItem>`. Use `asChild`-pattern (or your router's `<Link>`) on the anchor if you don't want a real `<a>`.
5. Insert `<PaginationItem><PaginationEllipsis /></PaginationItem>` between non-contiguous groups.
6. Add `<PaginationPrevious href={href(page - 1)} />` / `<PaginationNext href={href(page + 1)} />`. Hide them at boundaries (or `aria-disabled="true"` + `tabIndex={-1}` if you want them visible-but-inert).
7. If you used `pageSizes`: build a separate `<Select>` and own the `pageSize` state.
8. If you used `itemRangeText` / `itemsPerPageText`: render the strings yourself; consider an i18n helper.
9. If `Pagination` was inside a Carbon `DataTable` toolbar: it almost certainly needs the full Carbon-style footer; budget time to compose it.
