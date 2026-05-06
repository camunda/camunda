# ActionableNotification — Carbon → shadcn migration guide

## No 1-to-1 primitive

Carbon's `ActionableNotification` is a single component for "inline banner with a CTA". shadcn does not ship one primitive that covers this — instead, you pick the right tool for each variant:

- **Inline banner with a CTA** (the most common use): `Alert` (visual shell) + `Button` (the action). Compose them in a flex row.
- **Modal-style "are you sure?" confirmation**: `AlertDialog` (a blocking modal with built-in `AlertDialogAction` / `AlertDialogCancel` slots and Radix-managed focus trap).

The wrapper file (`actionable-notification.shadcn.tsx`) re-exports both primitive families for convenience; pick whichever matches the original usage.

## Decision rule

| Original Carbon usage | Migrate to |
|---|---|
| `<ActionableNotification inline>` (banner) | `Alert` + `Button` |
| `<ActionableNotification>` (toast-style, non-blocking) | `Sonner` (use the `toast-notification` migration when we get there) |
| Confirmation that blocks the page (rare with `ActionableNotification`) | `AlertDialog` |

Most Carbon ActionableNotifications in Camunda webapps are inline banners, so `Alert` + `Button` is the dominant migration target.

## Structural differences (banner case)

Carbon's `ActionableNotification` is **monolithic**: one component prop-driven by `kind`, `title`, `subtitle`, `actionButtonLabel`, `onActionButtonClick`, `inline`, `lowContrast`, `hideCloseButton`, etc.

shadcn's `Alert` + `Button` is **decomposed**: you assemble the layout (icon, title, description, action button) yourself.

| Concept | Carbon | shadcn |
|---|---|---|
| Wrapper | `<ActionableNotification kind="warning" />` | `<Alert variant="default">…</Alert>` |
| Icon | implicit (chosen from `kind`) | render manually as a child (e.g., from `lucide-react`) |
| Title | `title` prop | `<AlertTitle>` |
| Subtitle / body | `subtitle` prop | `<AlertDescription>` |
| Action button | `actionButtonLabel` + `onActionButtonClick` | `<Button>` placed inside the alert |
| Dismiss button | `hideCloseButton={false}` (default) | not modeled — render `<Button variant="ghost" size="icon">` with an X icon and your own onClick |
| Status / kind | `kind="success" / "info" / "warning" / "error"` | `variant="default" / "destructive"` (only two) — match other kinds via Tailwind classes |
| Inline vs. toast | `inline` boolean | always inline — use `Sonner` for toast |
| Low / high contrast | `lowContrast` boolean | not modeled — restyle via `className` |

## Carbon features missing in shadcn `Alert`

- **Status variants beyond default + destructive** — Carbon ships `success`, `info`, `warning`, `error` with built-in colour + icon. shadcn ships `default` + `destructive` only. For other kinds, override classes (`border-yellow-500/50 text-yellow-700` for warning, etc.) or extend `alertVariants` with new entries.
- **Built-in close button** — Carbon dismisses itself via the X. shadcn requires you to manage open state (e.g., `useState`) and render your own X button.
- **`actionButtonLabel` / `onActionButtonClick` props** — Carbon's CTA is a prop. shadcn requires explicit `<Button>` placement.
- **Inline icon by `kind`** — Carbon picks the icon for you. shadcn requires explicit `<Icon />` (typically lucide).
- **`statusIconDescription` (a11y)** — Carbon's icon SR label. shadcn's `Alert` already has `role="alert"`, so the title is announced; the icon is decorative by default (use `aria-hidden`).
- **`role="status"` toggling** — Carbon switches between `role="alert"` (errors) and `role="status"` (non-blocking) based on `kind`. shadcn always uses `role="alert"`.
- **`lowContrast`** — Carbon's flatter visual treatment. shadcn has no equivalent; restyle via `className`.
- **Skeleton state (`<NotificationSkeleton>`)** — Carbon ships a skeleton. shadcn has none — use the generic `skeleton` primitive.

## Carbon features missing in shadcn `AlertDialog`

(Most Carbon ActionableNotifications aren't modal, but `AlertDialog` is the right shadcn primitive for the rare case where they are.)

- **Built-in title icon by status** — `AlertDialog` is variant-less; render an icon inside `<AlertDialogHeader>` if you need one.
- **Auto-focus on cancel button (Carbon convention)** — Radix focuses the cancel button by default for destructive dialogs (matches Carbon's behaviour). Override via `onOpenAutoFocus`.

## shadcn features missing in Carbon

- **Tailwind composability** — `<Alert className>` accepts arbitrary classes; you can build `success`, `info`, `warning` variants by extending `alertVariants` in a few lines. Carbon's CSS is fixed.
- **`<AlertDialog>` Radix primitive** — full focus trap, scroll lock, and pointer-event blocking out of the box. Carbon's modal-confirmation requires the heavier `<Modal>` component.
- **`asChild`-style swap** — `<AlertDialogTrigger asChild>` lets any element open the dialog. Carbon requires explicit handler wiring.
- **Animation primitives** — entry/exit animations driven by `data-state` attributes.
- **Layered cancel/action buttons** — `<AlertDialogAction>` and `<AlertDialogCancel>` carry button styling and proper Radix wiring (Cancel auto-closes, Action runs your handler then closes). Less wiring than Carbon's manual `onActionButtonClick` + state.

## Behavioural differences

- **Default element** — Carbon: `<div role="alert">` for errors, `<div role="status">` for info. shadcn: always `<div role="alert">`.
- **Layout** — Carbon: 3-column flex (icon, content, action). shadcn: by default just stacked (`<AlertTitle>` + `<AlertDescription>`); you compose the icon and action manually.
- **Default border** — Carbon: solid border in the status colour. shadcn: 1px border via `border` token.
- **Animation** — Carbon: built-in slide/fade on mount and dismiss. shadcn `Alert`: none (use Tailwind transitions if needed). shadcn `AlertDialog`: animated by Radix.

## Migration checklist

### Inline banner (most common)

1. Replace `<ActionableNotification>` with `<Alert>` + a flex layout containing icon + title/description + action button.
2. Map `kind`:
   - `kind="error"` → `<Alert variant="destructive">` + `<AlertTriangle />` icon
   - `kind="warning"` → `<Alert>` + custom amber classes (`border-yellow-500/50 text-yellow-700`) + `<AlertTriangle />`
   - `kind="info"` → `<Alert>` + `<Info />` icon
   - `kind="success"` → `<Alert>` + custom green classes + `<CheckCircle />`
3. Move `title` to `<AlertTitle>`, `subtitle` to `<AlertDescription>`.
4. Replace `actionButtonLabel` + `onActionButtonClick` with `<Button onClick={…}>{label}</Button>` placed inside the Alert.
5. For dismissibility: track open state with `useState`, render an X button (`<Button variant="ghost" size="icon">`).
6. If you used `lowContrast`: restyle via `className` (e.g., remove the border, lighten the background).

### Modal confirmation (rare)

1. Switch to `<AlertDialog>` + `<AlertDialogTrigger>` + `<AlertDialogContent>`.
2. Move `title` → `<AlertDialogTitle>`, `subtitle` → `<AlertDialogDescription>`.
3. Replace the action button with `<AlertDialogAction>{label}</AlertDialogAction>` and a `<AlertDialogCancel>` for dismiss.
4. Wrap title/description in `<AlertDialogHeader>`, action/cancel in `<AlertDialogFooter>`.
5. Open/close state is owned by Radix — no `open` prop required unless you need controlled mode.
