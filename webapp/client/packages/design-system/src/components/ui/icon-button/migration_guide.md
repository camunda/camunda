# IconButton — Carbon → shadcn migration guide

## No 1-to-1 primitive

Carbon's `IconButton` is a **single-purpose component** for "square button containing only an icon, with a tooltip". It bundles three behaviours into one:
1. The button shell (variant, size, disabled state).
2. The tooltip showing `label` on hover/focus.
3. An accessible name automatically derived from `label`.

shadcn does **not** ship a dedicated icon button. The vanilla pattern is **`<Button variant="ghost" size="icon">` wrapped in `<Tooltip>`**, with `aria-label` set manually on the button. Both primitives are re-exported by this wrapper for migration ergonomics.

The result is more verbose at each call-site, but each piece (button styling, tooltip behaviour, a11y label) is owned by you, not the component.

## Structural differences

Carbon's `IconButton` is **monolithic**:
```tsx
<IconButton label="Add item" kind="ghost" align="bottom" size="md">
  <Plus />
</IconButton>
```

shadcn requires **explicit composition**:
```tsx
<TooltipProvider delayDuration={100}>
  <Tooltip>
    <TooltipTrigger asChild>
      <Button variant="ghost" size="icon" aria-label="Add item">
        <Plus className="h-4 w-4" />
      </Button>
    </TooltipTrigger>
    <TooltipContent>Add item</TooltipContent>
  </Tooltip>
</TooltipProvider>
```

If the page already has a top-level `<TooltipProvider>` (recommended), you only need the inner `Tooltip` part per icon button.

## Mapping

| Concept | Carbon `IconButton` | shadcn |
|---|---|---|
| Wrapper | `<IconButton>` | `<Button size="icon">` (+ `<Tooltip>` for label) |
| Icon | child element | child element |
| Accessible name | `label` prop (also drives the tooltip) | `aria-label` on `<Button>` AND `<TooltipContent>` text |
| Tooltip | built-in (always shown on hover/focus) | explicit `<Tooltip>` + `<TooltipTrigger asChild>` + `<TooltipContent>` |
| Tooltip position | `align="top" / "top-start" / "right" / …` | `<TooltipContent side="top" align="start">` (split into two props) |
| Tooltip enable/disable | always on | implicit; omit `<Tooltip>` to skip |
| Variant / kind | `kind="primary" / "secondary" / "tertiary" / "ghost" / "danger" / …` | `variant="default" / "outline" / "secondary" / "ghost" / "destructive" / "link"` |
| Size | `size="sm" / "md" / "lg"` (16/20/24px buttons) | `size="icon"` is one fixed size (40×40px); restyle via Tailwind for sm/md/lg variants |
| Selected / pressed state | `isSelected` prop | `data-state` + `aria-pressed`; restyle via `data-[state=pressed]:bg-accent` |
| Disabled | `disabled` | `disabled` (native HTML) |
| Drop shadow on tooltip | `tooltipDropShadow` | always on (`shadow-md`); strip via `<TooltipContent className="shadow-none">` |
| High contrast tooltip | `tooltipHighContrast` | not modeled — restyle `<TooltipContent>` |

## Carbon features missing in shadcn

- **`label` as a single prop driving both `aria-label` AND tooltip text** — shadcn requires you to set `aria-label` on the button AND put the same string inside `<TooltipContent>`. (You can extract a small helper in your app: `<IconButton label="Add">` that sets both for you.)
- **`size="sm" / "md" / "lg"`** — Carbon's icon button has 3 size variants. shadcn's `size="icon"` is a fixed 40×40px square. Mimic with custom Tailwind: `<Button size="icon" className="h-8 w-8 [&_svg]:size-3">` for "sm".
- **`align` compound positioning** — Carbon's tooltip placement uses single tokens like `top-start`, `bottom-end`. shadcn splits into `side` + `align`.
- **`isSelected`** — Carbon's selected-state styling for ghost icon buttons (used for toggles in toolbars). shadcn requires manual `data-[state=on]` styling via `<Toggle>` component, or `aria-pressed="true"` + custom classes.
- **`tooltipDropShadow={false}` / `tooltipHighContrast`** — Carbon tooltip variants. shadcn requires `className` overrides.
- **Built-in close-on-Escape with focus return** — Carbon's IconButton with isSelected has a clear pressed-state cycle. shadcn's `<Toggle>` handles this; an icon button is just a button.
- **`renderIcon`** — Carbon also accepts a render prop for the icon. shadcn expects icons as JSX children only.

## shadcn features missing in Carbon

- **`asChild` on the button** — `<Button size="icon" asChild><RouterLink>…</RouterLink></Button>` lets you ship an icon-styled link (Carbon requires a different component for that).
- **Tooltip provider model** — `<TooltipProvider>` centralises delay across the app; per-tooltip delays via separate providers. Carbon tooltips don't share delay state.
- **`<TooltipPrimitive.Arrow>`** — explicit arrow on the tooltip if you want it (shadcn's default is no arrow). Carbon's tooltip always has a caret.
- **Composability** — drop the `<Tooltip>` wrapper when not needed (e.g., when the icon button is part of a toolbar with its own labels). Carbon's IconButton always shows a tooltip.

## Behavioural differences

- **Default size** — Carbon: 32px (md). shadcn `size="icon"`: 40px.
- **Default variant** — Carbon: primary. shadcn: `default` (filled). Most icon buttons in real apps are `variant="ghost"` — set it explicitly.
- **Tooltip delay** — Carbon: ~100ms. shadcn (Radix default): 700ms; tighten via `<TooltipProvider delayDuration={100}>` to match.
- **Caret on tooltip** — Carbon: yes, by default. shadcn: no caret unless you add `<TooltipPrimitive.Arrow>`.
- **Border radius** — Carbon: square (0). shadcn: `rounded-md` (matches the rest of the button system).
- **Disabled tooltip behaviour** — Carbon: tooltip still shows. shadcn (Radix): trigger is disabled; if you want a tooltip on a disabled button, wrap the `<Button>` in a `<span tabIndex={0}>` so Tooltip can attach.

## Migration checklist

1. Replace `<IconButton label="…" kind="ghost">{icon}</IconButton>` with the shadcn composition (see `Default` story for the canonical shape).
2. Make sure the page tree has a `<TooltipProvider>` somewhere (typically at app root). Set `delayDuration={100}` to match Carbon's instant feel.
3. Set `aria-label` on the button AND put the same string in `<TooltipContent>`. Consider extracting a small `<IconButton>` helper in your app if you have many.
4. Map `kind` → `variant` per the table above.
5. Map `align` (e.g., `align="top-start"` → `<TooltipContent side="top" align="start">`).
6. If you used `size="sm" / "md" / "lg"`: add Tailwind size classes (`<Button size="icon" className="h-8 w-8 [&_svg]:size-3">` for sm, etc.) or extend `buttonVariants` with new icon sizes.
7. If you used `isSelected` for toolbar toggles: switch to shadcn `<Toggle>` component instead — that's the right primitive for binary on/off icon buttons.
8. If you want the caret/arrow on the tooltip: import `<TooltipPrimitive.Arrow>` from `@radix-ui/react-tooltip` and place it inside `<TooltipContent>`.
9. If using disabled icon buttons with tooltip: wrap the `<Button>` in `<span tabIndex={0}>` (otherwise Tooltip can't attach to a disabled element).
