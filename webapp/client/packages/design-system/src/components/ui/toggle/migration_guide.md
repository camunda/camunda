# Toggle — Carbon → shadcn migration guide

## ⚠️ Naming pitfall

shadcn ships **two components** that both have plausible-sounding names for "Toggle":

| shadcn primitive | What it is | Carbon equivalent |
|---|---|---|
| **`Switch`** | iOS-style sliding pill, binary on/off | **`Toggle`** ← use this |
| **`Toggle`** | Pressable button (radio-button-like, can be selected/deselected) | `IconButton` with `isSelected` (toolbar toggles) |

**Carbon `Toggle` → shadcn `Switch`**, NOT shadcn `Toggle`. This wrapper re-exports `Switch` for that reason.

If you have toolbar-style "press to toggle" icon buttons in Carbon, those map to shadcn's `Toggle` (or `<IconButton aria-pressed>`) — not to `Switch`. They're a different control.

## Structural differences

Carbon's `Toggle` is a **monolithic field**: switch + label + on/off text labels (`labelA`/`labelB`) all in one component.

shadcn's `Switch` is **just the switch** — a Radix-based binary control. Labels and on/off text are external composition.

| Concept | Carbon `Toggle` | shadcn `Switch` |
|---|---|---|
| Wrapper | `<Toggle labelText="…" />` | `<Switch />` (no label) |
| Field label | `labelText` prop | external `<label>` or `<Label htmlFor>` |
| Off-state text label | `labelA="Off"` | render manually as a sibling `<span>` |
| On-state text label | `labelB="On"` | render manually as a sibling `<span>` |
| Default state | `defaultToggled` | `defaultChecked` |
| Controlled state | `toggled` + `onToggle` | `checked` + `onCheckedChange` |
| Sizes | `size="sm" / "md"` | `size="sm" / "default"` |
| Disabled | `disabled` | `disabled` |
| Hide visible label | `hideLabel` | omit `<label>` and add `aria-label` to `<Switch>` |
| Skeleton | `<ToggleSkeleton>` | none — use the generic `skeleton` primitive |

## Carbon features missing in shadcn `Switch`

- **`labelText`** — Carbon's switch carries its own label. shadcn forces external composition.
- **`labelA` / `labelB` (on/off text labels)** — Carbon renders "Off" / "On" text next to the switch. shadcn requires manual sibling `<span>`s.
- **`hideLabel`** — Carbon's "label exists for SR but isn't shown" flag. shadcn: omit the `<label>` and add `aria-label` to the switch.
- **`<ToggleSkeleton>`** — Carbon ships a skeleton variant. shadcn has none — use the generic `skeleton` primitive sized to the switch shape.
- **`size="md"` (40px height)** — Carbon's default. shadcn: `size="default"` is `1.15rem` (~18.4px) tall, smaller than Carbon's `md`. To approximate Carbon size, restyle via Tailwind on the wrapper.

## shadcn features missing in Carbon

- **Pure Radix primitive** — `<Switch>` exposes Radix's `data-state="checked|unchecked"` attributes; restyle via `data-[state=checked]:bg-primary` etc.
- **Form integration with native `<input type="hidden">`** — Radix Switch automatically renders a hidden input mirror so it serializes in `<form>` submissions. Carbon's Toggle does this too via its native input.
- **Composability with `Field` / `Label`** — pair with shadcn's `Field` primitive for richer form layouts.
- **Animation** — Radix-driven smooth thumb translation via `transition-transform`. Carbon also animates but with different timing.

## Behavioural differences

- **Underlying primitive** — Carbon: native `<input type="checkbox" role="switch">` styled with CSS. shadcn: Radix `<button role="switch">` with a hidden `<input>` mirror.
- **Default size** — Carbon (`md`): 40px wide. shadcn (`default`): 32px wide (`w-8`).
- **Default colour when on** — Carbon: blue (`--cds-support-info`). shadcn: `bg-primary` (theme primary token).
- **Animation** — Both transition the thumb position smoothly. shadcn uses `transition-transform`.
- **Form serialization** — Both work natively in `<form>`. Carbon uses a real `<input>`; shadcn's Radix wraps the `<button>` with a hidden `<input>` mirror.

## Migration checklist

1. Replace `<Toggle labelText="…" />` with the shadcn composition:
   ```tsx
   <label className="flex items-center gap-2 text-sm">
     <Switch />
     <span>{labelText}</span>
   </label>
   ```
2. Remove `labelText`, render the text in the sibling `<span>`.
3. For `labelA="Off"` / `labelB="On"`: render two sibling `<span>`s flanking the switch:
   ```tsx
   <label className="flex items-center gap-2 text-sm">
     <span className="text-muted-foreground">{labelA}</span>
     <Switch />
     <span className="text-muted-foreground">{labelB}</span>
     <span>{labelText}</span>
   </label>
   ```
4. `defaultToggled` → `defaultChecked`.
5. `toggled` + `onToggle` → `checked` + `onCheckedChange`. Note: Carbon's `onToggle(checked, id, evt)` signature flattens; shadcn's `onCheckedChange(checked: boolean)` is just the boolean.
6. `size="sm"` → `size="sm"`. `size="md"` → drop the prop (default).
7. For `hideLabel`: drop the prop and add `aria-label` to `<Switch>`.
8. For `disabled`: maps directly.
9. **Don't migrate to shadcn `Toggle`** — that's the pressable-button primitive (used for toolbar toggles). Carbon's `Toggle` is a Switch.
10. If you need toolbar-style "selected" icon buttons (Carbon `IconButton` with `isSelected`): use shadcn `Toggle` (the *other* primitive) — it's the right one for that case.
