# Tile — Carbon → shadcn migration guide

## Variants and naming

Carbon ships **five tile variants** — each is its own component:
- `Tile` — basic content container (a styled `<div>`).
- `ClickableTile` — adds click handling, focus ring, hover state.
- `SelectableTile` — single-select tile (acts like a radio).
- `RadioTile` — explicit radio-tile (used inside `TileGroup`).
- `ExpandableTile` — collapsible tile with an expander button.

shadcn ships **one** primitive — `Card` — and you compose hover/click/select/expand behaviours yourself. The wrapper re-exports the canonical `Card` family from `card/`.

## Structural differences

Carbon's `Tile` is a plain `<div>` container; you fill it however you like.

shadcn's `Card` is **decomposed**: explicit `CardHeader` / `CardTitle` / `CardDescription` / `CardContent` / `CardFooter` / `CardAction` slots provide a consistent visual rhythm. Your title/description/content/footer have specific semantic roles.

| Concept | Carbon | shadcn |
|---|---|---|
| Wrapper | `<Tile>` | `<Card>` |
| Title | not modeled (use a raw `<h3>`) | `<CardTitle>` |
| Subtitle / description | not modeled (use a `<p>`) | `<CardDescription>` |
| Body content | direct children | `<CardContent>` |
| Footer / actions | not modeled (use a manual flex row) | `<CardFooter>` |
| Action button in header | not modeled | `<CardAction>` (auto-positioned via grid `col-start-2`) |
| Padding | manual (Carbon's `Tile` has no default padding; user must add `className`) | built-in via `CardHeader` / `CardContent` / `CardFooter` (`px-6` + `py-6` on the root) |
| Border | none by default | `border` token always on |
| Shadow | none by default | `shadow-sm` token always on |
| Background | `bg-layer-01` (Carbon layer system) | `bg-card` |
| Border radius | square (0) | `rounded-xl` (~12px) |
| Clickable | `<ClickableTile>` separate component | `<Card role="button" tabIndex={0} onClick onKeyDown>` (manual a11y) |
| Selectable | `<SelectableTile>` separate component | manual: track `selected` state, toggle `data-state` + visual classes |
| Radio (single-select group) | `<TileGroup><RadioTile />` | use shadcn `<RadioGroup>` with custom item rendering |
| Expandable | `<ExpandableTile>` separate component | use shadcn `<Collapsible>` (separate primitive — not yet wrapped) |
| Light variant | `light` boolean | `bg-card` token swap |

## Carbon features missing in shadcn

- **`<ClickableTile>`** — Carbon ships a clickable variant with focus ring + click handling baked in. shadcn requires manual composition: `role="button"`, `tabIndex={0}`, `onClick`, `onKeyDown` for Enter/Space, and a hover class.
- **`<SelectableTile>` / `<RadioTile>` / `<TileGroup>`** — Carbon's "tiles as form controls" family. shadcn requires composing with `<RadioGroup>` (separate primitive) + custom item rendering.
- **`<ExpandableTile>`** — Carbon's collapsible-tile pattern. shadcn requires composing with `<Collapsible>` (a shadcn primitive that's not yet wrapped in this design system; pull via `npx shadcn add collapsible`).
- **`light` prop** — Carbon's "light layer" treatment. shadcn: theme tokens (`bg-card` automatically swaps).
- **`<TileSkeleton>`** — Carbon ships a skeleton variant. shadcn has none — use the generic `skeleton` primitive in a Card shape.
- **No built-in title/description structure** — Carbon's `Tile` is a blank container; shadcn's `Card` enforces a header/footer rhythm with first-class slots.

## shadcn features missing in Carbon

- **`<CardTitle>` / `<CardDescription>` / `<CardHeader>` / `<CardFooter>`** — first-class slots with consistent typography and spacing. Carbon expects you to use raw elements.
- **`<CardAction>`** — auto-positioned action button slot (top-right via grid `col-start-2 row-span-2`). Useful for "edit" / "more" buttons inline with the title. Carbon requires manual flex layout.
- **Built-in padding** — `CardHeader`/`CardContent`/`CardFooter` handle their own padding (`px-6`). Carbon's `Tile` has none — every consumer adds `p-N` themselves.
- **Built-in border + shadow** — out of the box, you get a styled card without writing classes. Carbon's tile is unstyled.
- **`@container/card-header`** — `CardHeader` is a CSS container, so you can use `@container` queries inside it for responsive layouts. Carbon has no equivalent.
- **`[.border-b]:pb-6` / `[.border-t]:pt-6` selectors** — `CardHeader` adds bottom padding when it has a `border-b`, `CardFooter` adds top padding when it has a `border-t`. Tiny but very nice.
- **Tailwind composability** — every part accepts `className`; restyle inline.

## Behavioural differences

- **Default element** — Both: `<div>`.
- **Default padding** — Carbon: 0 (you add it). shadcn: `py-6` on the root, `px-6` on each section.
- **Default border** — Carbon: 0 (you add it). shadcn: 1px theme border always on.
- **Default shadow** — Carbon: 0. shadcn: `shadow-sm` always on (override with `shadow-none` if needed).
- **Default radius** — Carbon: 0 (square). shadcn: `rounded-xl` (~12px).
- **Default gap between sections** — Carbon: none (no sections). shadcn: `gap-6` between `CardHeader`/`CardContent`/`CardFooter`.

## Migration checklist

1. Replace `<Tile className="p-6">{content}</Tile>` with `<Card>` + appropriate slots:
   - Title → `<CardHeader><CardTitle>{title}</CardTitle></CardHeader>`
   - Subtitle → `<CardDescription>` inside `<CardHeader>`
   - Body → `<CardContent>{body}</CardContent>`
   - Buttons / actions → `<CardFooter>{buttons}</CardFooter>`
2. Drop the manual `p-N` class — `Card`'s built-in padding (`py-6` + `px-6` per section) replaces it.
3. For `<ClickableTile>`: use `<Card role="button" tabIndex={0} onClick={…} onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); … } }} className="cursor-pointer hover:bg-muted/50 transition-colors">`. Or use `asChild` with a real `<button>`/`<a>` to inherit native semantics.
4. For `<SelectableTile>` (single-select): track `useState`, toggle `data-state` + a `border-primary` class on the selected card, attach click handlers. For accessibility, wrap a group in `role="radiogroup"` and give each card `role="radio"` + `aria-checked`.
5. For `<RadioTile>` + `<TileGroup>`: pull in shadcn's `radio-group` primitive (`npx shadcn add radio-group`) and render Card-styled radio items. Consider extracting a project-local `<RadioCard>` helper.
6. For `<ExpandableTile>`: pull in shadcn's `collapsible` primitive (`npx shadcn add collapsible`). Wrap `<Card>` in `<Collapsible>`, place a chevron button in `<CardAction>` as the trigger, body content in `<CollapsibleContent>`.
7. For `<TileSkeleton>`: build a `<Card>` shell with `<Skeleton>` rectangles for title/description/content.
8. To match Carbon's flatter default look (no border, no shadow, square): `<Card className="rounded-none border-0 shadow-none">`.
9. **Don't migrate Carbon `Tile` to a generic `<div>`** — even if your tile has no title/description structure, using `<Card>` ensures consistent padding/border/shadow tokens with the rest of the design system.
