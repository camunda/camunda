# Callout — Carbon → shadcn migration guide

## What `Callout` is

Carbon's `Callout` (a.k.a. `StaticNotification`) is a **permanent, non-dismissible inline notification** — meant for contextual messages that stay on the page (e.g., "Some changes cannot be undone" inside a settings panel). It's the "static" cousin of `ActionableNotification` and `InlineNotification`.

shadcn ships exactly one primitive for inline notifications: **`Alert`**. The canonical source lives at `alert/alert.shadcn.tsx`; this wrapper re-exports it for migration ergonomics.

## Structural differences

Carbon's `Callout` is **monolithic**: one component, prop-driven via `kind`, `title`, `subtitle`, `actionButtonLabel`, `onActionButtonClick`, `lowContrast`, `statusIconDescription`.

shadcn's `Alert` is **decomposed**: assemble the icon, title, description, and optional CTA button yourself.

| Concept | Carbon | shadcn |
|---|---|---|
| Wrapper | `<Callout kind="info" />` | `<Alert>…</Alert>` |
| Icon | implicit (chosen from `kind`) | render manually as a child (e.g., `lucide-react` `<Info />` / `<AlertTriangle />`) |
| Title | `title` prop | `<AlertTitle>` |
| Subtitle / body | `subtitle` prop | `<AlertDescription>` |
| Action button | `actionButtonLabel` + `onActionButtonClick` | `<Button>` placed inside the Alert (typically `variant="outline"` to match Callout's quiet CTA) |
| Status / kind | `kind="info"` / `"warning"` (only 2!) | `variant="default"` / `"destructive"` (only 2!) — neither covers info+warning natively |
| Low contrast | `lowContrast` boolean | not modeled — restyle via `className` |
| Status icon a11y | `statusIconDescription` | render the icon as decorative (`aria-hidden`); title is announced by `role="alert"` |

## Carbon features missing in shadcn `Alert`

- **`kind="info"` / `"warning"` built-in styling** — Carbon picks blue (info) or amber (warning) palette for you. shadcn ships only `default` (theme bg) and `destructive` (red). Map `kind` to a custom `className` (or extend `alertVariants`):
  - `kind="info"` → `className=""` (default look) + `<Info />` icon
  - `kind="warning"` → `className="border-yellow-500/50 text-yellow-700 dark:border-yellow-500 [&>svg]:text-yellow-600"` + `<AlertTriangle />`
- **`lowContrast`** — Carbon's quieter variant (no border, lighter background). shadcn has no toggle; mimic with `className="border-transparent bg-muted"`.
- **`statusIconDescription`** — Carbon's SR-only label for the status icon. shadcn relies on `role="alert"` announcing the title; the icon is decorative. If you specifically need an SR label on the icon, add `<span className="sr-only">…</span>`.
- **`actionButtonLabel` / `onActionButtonClick` props** — Carbon's CTA is a prop. shadcn requires explicit `<Button>` placement.
- **Built-in icon by `kind`** — Carbon picks the icon for you. shadcn requires an explicit icon child.
- **Skeleton state (`<NotificationSkeleton>`)** — Carbon ships a skeleton. shadcn has none — use the generic `skeleton` primitive.

## shadcn features missing in Carbon

- **Tailwind composability** — `<Alert className>` accepts arbitrary classes; build success/info/warning/error variants by extending `alertVariants` in 4 lines, or compose ad-hoc per usage.
- **Decomposed structure** — render the icon, title, description, and CTA wherever you want inside the Alert. Carbon's slot order is fixed.
- **Universal primitive** — the same `Alert` is used for `Callout`, `ActionableNotification` (inline), and `InlineNotification`. One thing to learn, restyled per use case.

## Behavioural differences

- **Default element** — Both render `role="alert"` divs.
- **Layout** — Carbon: 3-column flex (icon, content, optional action). shadcn: by default a vertical stack (`<AlertTitle>` + `<AlertDescription>`); compose icon + action manually.
- **Default border** — Carbon (high contrast): solid border in the status colour. shadcn: 1px theme border.
- **Animation** — Carbon: built-in slide/fade on mount. shadcn: none — add Tailwind transitions if needed (uncommon for static callouts).

## Migration checklist

1. Replace `<Callout>` with `<Alert>` plus a flex layout containing icon + title/description (+ optional action button).
2. Map `kind`:
   - `kind="info"` → default `<Alert>` + `<Info />` from `lucide-react`
   - `kind="warning"` → `<Alert className="border-yellow-500/50 text-yellow-700 [&>svg]:text-yellow-600">` + `<AlertTriangle />`
3. Move `title` to `<AlertTitle>`, `subtitle` to `<AlertDescription>`.
4. If `actionButtonLabel` was set: add `<Button variant="outline" size="sm">{label}</Button>` placed inside the Alert (typical Callout CTA is quiet, so `outline`/`ghost` matches better than `default`).
5. If `lowContrast` was set: add `className="border-transparent bg-muted"` to mute the visual weight.
6. Drop `statusIconDescription` unless your icon is non-decorative; add an `<span className="sr-only">…</span>` next to the icon if needed.
7. If multiple Callouts in the same view: consider extracting your status-mapped Alert into a tiny local helper (`<InfoCallout>`, `<WarningCallout>`) or extend `alertVariants` with `info` / `warning` entries to centralise the styling.
