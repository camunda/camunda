# OverflowMenu — Carbon → shadcn migration guide

## Same primitive as MenuButton

Carbon ships **two action-menu components** that share UX but differ in trigger appearance:
- **`MenuButton`** — labelled trigger ("Actions" + chevron). Covered separately under `menu-button/`.
- **`OverflowMenu`** — icon-only trigger (`⋮` vertical ellipsis). **This guide.**

Both map to **shadcn `DropdownMenu`** — only the trigger composition differs. The wrapper re-exports the same canonical `DropdownMenu*` family from `dropdown-menu/`.

## Trigger composition

The shadcn equivalent of Carbon's icon-only overflow trigger is `<Button variant="ghost" size="icon">` containing a `MoreVertical` (or `MoreHorizontal`) icon from `lucide-react`. `aria-label` MUST be set since there is no visible text:

```tsx
<DropdownMenu>
  <DropdownMenuTrigger asChild>
    <Button variant="ghost" size="icon" aria-label="Row actions">
      <MoreVertical className="h-4 w-4" />
    </Button>
  </DropdownMenuTrigger>
  <DropdownMenuContent align="end">
    <DropdownMenuItem>Edit</DropdownMenuItem>
    <DropdownMenuItem>Duplicate</DropdownMenuItem>
    <DropdownMenuSeparator />
    <DropdownMenuItem variant="destructive">Delete</DropdownMenuItem>
  </DropdownMenuContent>
</DropdownMenu>
```

If you want a tooltip on the trigger (Carbon shows the `aria-label` as a tooltip on hover by default), wrap with `<Tooltip>` — see the `icon-button/` migration guide for the full recipe.

## Mapping

| Concept | Carbon `OverflowMenu` | shadcn `DropdownMenu` |
|---|---|---|
| Wrapper | `<OverflowMenu aria-label="…">` | `<DropdownMenu>` (root) |
| Trigger | implicit (`⋮` icon-only button) | `<DropdownMenuTrigger asChild><Button variant="ghost" size="icon"><MoreVertical /></Button></DropdownMenuTrigger>` |
| Menu | implicit | `<DropdownMenuContent align>` |
| Action item | `<OverflowMenuItem itemText="Edit" onClick />` | `<DropdownMenuItem onSelect>Edit</DropdownMenuItem>` |
| Destructive item | `<OverflowMenuItem isDelete />` | `<DropdownMenuItem variant="destructive" />` |
| Visual divider above an item | `<OverflowMenuItem hasDivider />` | render `<DropdownMenuSeparator />` before the item |
| Disabled item | `<OverflowMenuItem disabled />` | `<DropdownMenuItem disabled />` |
| Required-confirmation item | `<OverflowMenuItem requireTitle wrapperClassName />` | not modeled — wrap action with your own confirm dialog |
| Disabled trigger | `disabled` on `<OverflowMenu>` | `disabled` on the inner `<Button>` |
| Trigger position | `direction="top" / "bottom"` + `flipped` | `<DropdownMenuContent side align>` |
| Trigger ariaLabel | `aria-label` on `<OverflowMenu>` | `aria-label` on the inner `<Button>` |
| Trigger icon | `renderIcon` (defaults to `⋮`) | render any icon as child of `<Button>` (lucide `MoreVertical` / `MoreHorizontal`) |
| Light variant | `light` boolean | restyle via Tailwind tokens |
| Custom trigger | `renderIcon` only | full `asChild` flexibility — any element |

## Carbon features missing in shadcn

- **`renderIcon` for the trigger** — Carbon's prop to swap the default `⋮`. shadcn: render whatever icon you want as child of `<Button>`, or use a custom trigger element entirely via `asChild`.
- **`flipped` boolean** — Carbon's "open the other way" toggle (originally for RTL / right-aligned). shadcn: explicit `<DropdownMenuContent align="end">` and/or `side` props.
- **`direction="top" / "bottom"`** — Carbon's vertical placement. shadcn: `<DropdownMenuContent side="top" / "bottom">`.
- **`onClose` lifecycle** — Carbon callback when menu closes. shadcn: `<DropdownMenu onOpenChange={(open) => !open && handleClose()}>`.
- **`size` (`sm` / `md` / `lg`)** — Carbon trigger sizing. shadcn: pass via `<Button size>` (default-style button has `default`/`sm`/`lg`/`icon`).
- **`requireTitle` / wrapper styling for destructive items** — Carbon's pattern for items that need confirmation. shadcn requires composing with `<AlertDialog>` (action menu opens → user picks "Delete" → confirm dialog appears).
- **Skeleton variant** — Carbon ships `<OverflowMenuSkeleton>`. shadcn has none — use the generic `skeleton` primitive.
- **`tabIndex` controls** — Carbon's deep `tabIndex` props for menu items. shadcn (Radix) handles tabbing automatically; opt out with `tabIndex={-1}` if needed.

## shadcn features missing in Carbon

- **`asChild` trigger** — `<DropdownMenuTrigger asChild>` accepts any element (icon button, badge, custom trigger).
- **First-class submenus** — `<DropdownMenuSub>` + `<DropdownMenuSubTrigger>` + `<DropdownMenuSubContent>` for nested action menus. Carbon `OverflowMenu` doesn't support submenus.
- **`<DropdownMenuLabel>` / `<DropdownMenuShortcut>`** — header labels and keyboard-shortcut hints inside the menu. No Carbon equivalent.
- **`<DropdownMenuRadioGroup>` / `<DropdownMenuCheckboxItem>`** — first-class checkbox/radio in menus. Carbon's overflow menu items are click-only.
- **Animation primitives** — entry/exit animations driven by `data-state`.
- **Portal-rendered content** — content renders into `<body>` so it's never clipped by ancestor `overflow:hidden` (e.g., a table row's overflow). Carbon's overflow menu also portals in modern versions.

## Behavioural differences

- **Default open trigger** — Both: click on trigger or Enter/Space.
- **Close** — Both: click outside, Escape, item select.
- **Tooltip on the trigger** — Carbon shows the `aria-label` as a tooltip on hover. shadcn: no automatic tooltip; wrap with `<Tooltip>` if needed.
- **Z-index** — shadcn: `z-50`. Carbon: token-driven.
- **Default alignment** — Carbon: typically left-aligned to trigger. shadcn `<DropdownMenuContent>`: defaults `align="center"`. For overflow-menus on table rows, you usually want `align="end"` so the menu opens to the left of the trigger.

## Migration checklist

1. Replace `<OverflowMenu aria-label="X">` with the canonical composition:
   ```tsx
   <DropdownMenu>
     <DropdownMenuTrigger asChild>
       <Button variant="ghost" size="icon" aria-label="X">
         <MoreVertical className="h-4 w-4" />
       </Button>
     </DropdownMenuTrigger>
     <DropdownMenuContent align="end">…</DropdownMenuContent>
   </DropdownMenu>
   ```
2. Replace each `<OverflowMenuItem itemText onClick />` with `<DropdownMenuItem onSelect>{text}</DropdownMenuItem>`.
3. For `isDelete` items: `<DropdownMenuItem variant="destructive">`. Add `<DropdownMenuSeparator />` before them if `hasDivider` was set.
4. For `disabled` on items: maps directly.
5. For `disabled` on the menu trigger: pass `disabled` to the inner `<Button>`.
6. Translate `direction="top" + flipped` → `<DropdownMenuContent side="top" align="end">`.
7. For tooltip on trigger: wrap the trigger button in `<Tooltip>` (see `icon-button/migration_guide.md`).
8. For confirmation-required items (`requireTitle`): chain into `<AlertDialog>` — action menu picks "Delete" → AlertDialog confirms.
9. If you want the icon button to look quieter (no hover background): use `variant="ghost"`. For a stronger affordance, use `variant="outline"`.
10. For consistency across rows in a list/table: extract a small `<RowActions>` helper component to avoid repeating the composition.
