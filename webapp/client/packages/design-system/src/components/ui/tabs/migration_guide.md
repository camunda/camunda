# Tabs — Carbon → shadcn migration guide

## Structural differences

Carbon's tabs are a **5-component tree** with positional matching: triggers and panels live in two separate containers, paired by index.

shadcn's tabs are a **3-component tree** with explicit `value` matching: each trigger and each panel carries the same `value` string.

| Concept | Carbon | shadcn |
|---|---|---|
| Root | `<Tabs>` | `<Tabs value=\"…\">` (controlled) or `<Tabs defaultValue=\"…\">` (uncontrolled) |
| Tab list | `<TabList aria-label=\"…\">` | `<TabsList>` |
| Tab trigger | `<Tab>` | `<TabsTrigger value=\"…\">` |
| Panel container | `<TabPanels>` | not modeled — panels are direct children of `<Tabs>` |
| Tab panel | `<TabPanel>` | `<TabsContent value=\"…\">` |
| Pairing | by **child index** (1st `<Tab>` → 1st `<TabPanel>`) | by **`value` string** |

The pairing-by-string is the single most important difference: in shadcn you can reorder triggers, conditionally render panels, render panels far from the list, and they still link up by `value`. In Carbon the order has to match.

## Carbon features missing in shadcn

- **`<TabPanels>` container** — Carbon wraps its panels in a `<div>` for layout/styling. shadcn renders panels as direct children of `<Tabs>` (with `mt-2` default spacing on each `<TabsContent>`). Restyle via `className` if needed.
- **`<TabList contained>`** — Carbon's contained / pill variant. shadcn's default is already a contained pill style (rounded, muted background, active item gets a card surface).
- **`<TabList iconSize=\"lg\">`** — icon-tab sizing variant. shadcn has no equivalent — size icons via Tailwind classes inside `<TabsTrigger>`.
- **`<IconTab>` / `<TabList renderIcon>`** — Carbon's icon-only tab variant with built-in tooltip. shadcn requires composing `<TabsTrigger>` + `<Tooltip>` manually.
- **`<TabList scrollIntoView>` / overflow indicator chevrons** — Carbon's overflow buttons appear when tabs don't fit. shadcn uses native horizontal scroll; add `overflow-x-auto` and your own scroll affordances.
- **`<TabList aria-label>`** — Carbon requires `aria-label` on the tab list (announced by SR). shadcn's `<TabsList>` has implicit `role=\"tablist\"`; add `aria-label` via `className`/spread if you need an explicit label (recommended for accessibility).
- **`<Tab disabled>` propagating to its panel** — In Carbon, disabling a tab also blocks panel access. shadcn's `<TabsTrigger disabled>` only blocks the trigger; the panel can still render via direct manipulation of `value`.
- **Vertical tab variant (`TabsVertical`, `TabListVertical`)** — Carbon ships a vertical layout. shadcn does not — implement with `flex-col` on the list and adjust the active-state indicator manually.
- **`onChange({selectedIndex})`** — Carbon's change handler reports a numeric index. shadcn's `onValueChange(value: string)` reports the value string.
- **`<TabsSkeleton>`** — Carbon ships a skeleton variant. shadcn has none — use the generic `skeleton` primitive.

## shadcn features missing in Carbon

- **Pair-by-`value`** — explicit string matching means panels can be rendered in a different order or location from triggers. Carbon's pair-by-index is structurally rigid.
- **Controlled API** — `value` + `onValueChange` for full state ownership; or `defaultValue` for uncontrolled. Carbon's API is index-based and less ergonomic for routing-driven tab state (e.g., `?tab=…` URL params).
- **`forceMount` on `<TabsContent>`** — Radix lets you keep panels mounted even when inactive (useful for forms with state to preserve). Carbon mounts/unmounts on switch.
- **Animation primitives** — entry/exit animations driven by `data-state` attributes; tweak via Tailwind's `data-[state=active]:animate-in`. Carbon's CSS transitions are baked in.
- **`asChild`-style swap** — every part accepts `asChild` (Radix slot pattern) so you can render a router `<Link>` as a trigger.

## Behavioural differences

- **State model** — Carbon: zero-or-more selected (typically tab index), uncontrolled-by-default. shadcn: exactly-one selected (`value` string), uncontrolled-by-default.
- **Default visual** — Carbon (un-contained): underline-style indicator. Carbon (contained): button-pill style. shadcn: pill style by default (rounded muted container, active tab is a white card).
- **Keyboard** — Both support arrow keys to switch tabs. shadcn additionally respects `Home`/`End` (jump to first/last) and `activationMode=\"manual\"` (require Enter/Space to activate, vs. follow-focus default).
- **Indicator animation** — Carbon: animated underline that slides between tabs. shadcn: card-surface fade transition; no slider.
- **Mount semantics** — Carbon: panel mounts on activation. shadcn: same by default; flip with `forceMount` to keep them in the DOM.

## Migration checklist

1. Restructure children:
   - `<Tabs>` wraps everything (same).
   - `<TabList>` → `<TabsList>`. Drop `<TabPanels>` — panels are now direct children of `<Tabs>`.
   - Each `<Tab>label</Tab>` becomes `<TabsTrigger value=\"…\">label</TabsTrigger>`.
   - Each `<TabPanel>{content}</TabPanel>` becomes `<TabsContent value=\"…\">{content}</TabsContent>`.
2. Choose `value` strings for each tab. Use stable identifiers (e.g., `account`, `password`) — they'll appear in `onValueChange` and pair with panels.
3. If you used `selectedIndex`/`onChange({selectedIndex})`: switch to `value`/`onValueChange(value: string)`. Maintain a value-to-index map only if downstream code requires it.
4. Add `aria-label=\"…\"` on `<TabsList>` if your tabs aren't surrounded by a heading that gives them an accessible name (a11y best practice; Radix doesn't enforce it).
5. If you used `<TabList contained>`: drop the prop — shadcn's default is already pill-styled. If you want the underline-style instead, restyle via `className` or build a variant of `<TabsList>` / `<TabsTrigger>`.
6. If you used `<IconTab>`: compose `<TabsTrigger>` with an icon child + a `<Tooltip>` for the label; manage `aria-label` yourself.
7. If you used vertical tabs: add `flex-col` to `<TabsList>` and add a vertical active-state indicator via Tailwind on `<TabsTrigger>` (e.g., `data-[state=active]:border-l-2`).
8. If you used overflow chevrons: add `overflow-x-auto` to `<TabsList>` and accept native scroll, or build custom prev/next buttons.
9. If you used the `<TabsSkeleton>`: replace with the generic shadcn `skeleton` primitive applied to a fake `<TabsList>` shape.
