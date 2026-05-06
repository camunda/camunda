# TextArea — Carbon → shadcn migration guide

## Naming

- Carbon: `TextArea` (PascalCase, two words).
- shadcn: `Textarea` (PascalCase, one word).

Trivial but easy to miss in find-and-replace.

## Structural differences

Carbon's `TextArea` is a **monolithic field** — input, label, helper text, character counter, error/warning text all live inside one component, configured by props.

shadcn's `Textarea` is **just a styled `<textarea>`** — exactly that, nothing else. Labels, helper text, counters, error text are external composition.

| Concept | Carbon | shadcn |
|---|---|---|
| Wrapper / element | `<TextArea labelText="…" />` (compound) | `<Textarea />` (raw `<textarea>`) |
| Label | `labelText` prop | external `<label>` or `<Label htmlFor>` |
| Helper text | `helperText` prop | external `<p>` |
| Invalid + error text | `invalid` + `invalidText` | `aria-invalid` + external `<p>`; restyle border via `className` |
| Warn state | `warn` + `warnText` | not modeled — render manually |
| Read-only | `readOnly` | `readOnly` (native HTML) |
| Character counter | `enableCounter` + `maxCount` | not modeled — render `{value.length}/{max}` yourself |
| Hide visible label | `hideLabel` | render no `<label>` (set `aria-label` on the textarea) |
| Wrapper class hooks | `className` (on wrapper) | `className` (on the textarea itself — there is no wrapper) |
| Skeleton | `<TextAreaSkeleton>` | none — use the generic `skeleton` primitive |

## Carbon features missing in shadcn

- **`labelText` / `helperText` / `invalidText` / `warnText`** — Carbon ships labels and messaging as props. shadcn forces external composition (typically with the `Field` primitive).
- **`warn` state** — Carbon distinguishes invalid (red) from warning (amber). shadcn has only `aria-invalid`. Mimic warning by hand-rolling amber classes.
- **`enableCounter` + `maxCount` + `counterMode`** — Carbon's built-in character counter ("3/280" or "wordcount" mode). shadcn requires you to track length and render the counter yourself.
- **`hideLabel`** — Carbon's "label exists for SR but isn't shown" flag. shadcn requires you to omit the visible `<label>` and add `aria-label` directly.
- **`readOnly`** styling — Both support `readOnly`, but Carbon greys the background and changes border colour automatically. shadcn keeps the same look (only the cursor and editing behaviour change); add `cursor-not-allowed bg-muted` via `className` to mimic.
- **`<TextAreaSkeleton>`** — Carbon ships a skeleton. shadcn has none.
- **Field-level focus management** — Carbon's wrapper handles focus rings around the entire compound (label + textarea + helper). shadcn focuses just the textarea.
- **Localized messaging** — Carbon's `helperText`/`invalidText` slots are i18n hooks. shadcn requires you to localize the strings yourself.

## shadcn features missing in Carbon

- **Pure native `<textarea>`** — every native attribute (`autoComplete`, `wrap`, `spellCheck`, `dir`, `inputMode`, etc.) just works without prop forwarding gymnastics. Carbon filters props in some cases.
- **Tailwind-first sizing** — change `min-h`, `w`, padding, borders via `className`. Carbon's sizing is token-driven and locked.
- **Composability with `Field` / `Label`** — pair with shadcn's `Field`, `FieldGroup`, `FieldDescription` to build complex forms. Form integration with React Hook Form / TanStack Form is straightforward — `register('description')` works as expected on the underlying native textarea.
- **`md:text-sm` responsive sizing** — shadcn's textarea uses `text-base` on small screens, `text-sm` on `md+`. Carbon is fixed at one size.

## Behavioural differences

- **Underlying element** — both render a real `<textarea>`. Form serialisation works on both.
- **Default min-height** — Carbon: token-driven (typically `40px` × number of rows). shadcn: `min-h-[80px]` (about 4 rows). Use the `rows` HTML attribute to control height in either.
- **Default font size** — Carbon: 14px. shadcn: 16px on mobile, 14px on `md+`.
- **Border radius** — Carbon: `0` (square). shadcn: `rounded-md` (6px).
- **Resize affordance** — Both inherit browser default (`resize: vertical` / `both` depending on UA). Override via `resize-none` / `resize-y` / `resize` Tailwind classes.
- **Disabled styling** — Carbon: greys background + border. shadcn: drops opacity to 50% and `cursor-not-allowed`.

## Migration checklist

1. Rename: `TextArea` → `Textarea`.
2. Move `labelText` to a sibling `<label>` (or `<Label htmlFor>`).
3. Move `helperText` to a sibling `<p className="text-xs text-muted-foreground">`.
4. For `invalid` + `invalidText`: add `aria-invalid` on `<Textarea>`, add `className="border-destructive focus-visible:ring-destructive"`, and render the error text in a sibling `<p className="text-xs text-destructive">`.
5. For `warn` + `warnText`: render amber styling by hand — no shadcn primitive.
6. For `enableCounter` + `maxCount`: track value length yourself and render `<p className="text-xs text-muted-foreground">{value.length}/{maxCount}</p>`. Set `maxLength` on `<Textarea>` to prevent typing past the limit.
7. For `readOnly`: keep the prop (native HTML supports it), but optionally add `className="bg-muted cursor-not-allowed"` to mimic Carbon's greyed look.
8. For `hideLabel`: drop the prop; either omit the `<label>` and add `aria-label` to `<Textarea>`, or wrap the `<label>` in `<span className="sr-only">`.
9. For form libraries: works out of the box with `react-hook-form` / `react-final-form` / `formik` because the underlying textarea is native.
10. For skeleton state: use the generic shadcn `skeleton` primitive sized to mimic the textarea (e.g., `<Skeleton className="h-20 w-full" />`).
