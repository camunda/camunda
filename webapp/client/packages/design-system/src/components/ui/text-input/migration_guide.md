# TextInput — Carbon → shadcn migration guide

## Naming

- Carbon: `TextInput` (PascalCase, two words).
- shadcn: `Input` (PascalCase, one word).

Same behaviour at the core (a styled `<input>`); different name and very different surface area.

## Structural differences

Carbon's `TextInput` is a **monolithic field**: input, label, helper text, error text, and warning text all live inside one component, configured by props (`labelText`, `helperText`, `invalidText`, `warnText`).

shadcn's `Input` is **just a styled `<input>`** — exactly that, nothing else. Labels, helper text, and error text are external composition (typically with the shadcn `Field` primitive, or by hand).

| Concept | Carbon | shadcn |
|---|---|---|
| Wrapper / element | `<TextInput labelText="…" />` (compound) | `<Input />` (raw `<input>`) |
| Label | `labelText` prop | external `<label>` or `<Label htmlFor>` |
| Helper text | `helperText` prop | external `<p>` |
| Invalid + error text | `invalid` + `invalidText` | `aria-invalid` + external `<p>`; built-in red border kicks in via CSS |
| Warn state | `warn` + `warnText` | not modeled — render manually |
| Read-only | `readOnly` | `readOnly` (native HTML) |
| Hide visible label | `hideLabel` | render no `<label>` (set `aria-label` on the input) |
| Placeholder | `placeholder` prop | `placeholder` (native HTML) |
| Sizes | `size="sm" / "md" / "lg"` | none — restyle `h-N`, `text-N` via Tailwind |
| Specialised types | separate components: `<PasswordInput>`, `<NumberInput>`, `<SearchInput>` | `<Input type="password" / "number" / "search" />` (native HTML) |
| Show-password toggle | `<PasswordInput>` ships one | not modeled — render eye icon + toggle state manually |
| Skeleton | `<TextInputSkeleton>` | none — use the generic `skeleton` primitive |

## Carbon features missing in shadcn

- **`labelText` / `helperText` / `invalidText` / `warnText`** — Carbon ships labels and messaging as props. shadcn forces external composition.
- **`warn` state** — Carbon distinguishes invalid (red) from warning (amber). shadcn has only `aria-invalid`. Mimic warning by hand-rolling amber classes.
- **`size="sm" / "md" / "lg"`** — Carbon density variants (32/40/48px tall). shadcn `<Input>` is `h-9` (36px); restyle with Tailwind for sm/lg.
- **`light` prop** — Carbon's light-on-dark variant. shadcn relies on theme tokens.
- **`<PasswordInput>` separate component** — Carbon ships a dedicated password component with built-in show/hide toggle and visibility icons. shadcn: `<Input type="password" />` is just the native input; for the toggle, compose with a `<Button>`.
- **`<NumberInput>` separate component** — Carbon ships dedicated stepper UI (`+`/`−` buttons + parsing). shadcn: `<Input type="number" />` is native; for stepper buttons, compose with `<Button>`s and `useState`.
- **`<SearchInput>` separate component** — Carbon's search variant with built-in search icon and clear button. shadcn: compose `<Input type="search">` with a leading `<Search />` icon and a clear `<Button>` (typical pattern uses `<InputGroup>`).
- **`inline` prop** — Carbon's inline-label variant where the label sits beside the input. shadcn requires custom flexbox composition.
- **Skeleton** — Carbon ships `<TextInputSkeleton>`. shadcn has none.
- **Field-level focus management** — Carbon's wrapper handles focus rings around the entire compound. shadcn focuses just the input.

## shadcn features missing in Carbon

- **Pure native `<input>`** — every native attribute (`autoComplete`, `inputMode`, `pattern`, `step`, `min`, `max`, `list`, `dir`, `spellCheck`) just works without prop forwarding gymnastics. Carbon filters props in some cases.
- **Tailwind composability** — change `h`, `w`, padding, borders, background via `className`. Carbon's sizing is token-driven and locked.
- **`md:text-sm` responsive sizing** — shadcn defaults `text-base` on small screens, `text-sm` on `md+`. Carbon is fixed.
- **`aria-invalid` styling out of the box** — `<Input aria-invalid>` automatically gets the destructive border + ring via the built-in `aria-invalid:` selectors.
- **Composability with `Field` / `Label`** — pair with shadcn's `Field`, `FieldGroup`, `FieldDescription` to build complex forms. Form integration with React Hook Form / TanStack Form is straightforward — `register('email')` works as expected on the underlying native input.
- **`InputGroup` family** — pair with `<InputGroup>` to add icon prefixes/suffixes (we already pulled this in for combo-box). Carbon: similar pattern requires `inline` + manual layout.

## Behavioural differences

- **Underlying element** — both render a real `<input>`. Form serialisation works on both.
- **Default height** — Carbon (md): 40px. shadcn: `h-9` (36px). For Carbon-like density, use `<Input className="h-10">`.
- **Default font size** — Carbon: 14px. shadcn: 16px on mobile, 14px on `md+`.
- **Border radius** — Carbon: `0` (square). shadcn: `rounded-md` (~6px).
- **Background** — Carbon: greyish field background. shadcn: transparent (so it picks up the surrounding card/popover bg).
- **Focus ring** — Carbon: 2px inset blue. shadcn: 3px outset ring with `--ring/50` colour.
- **Disabled styling** — Carbon: greys background + border. shadcn: drops opacity to 50% and `cursor-not-allowed`.
- **`aria-invalid` styling** — Carbon: only when `invalid` prop is set. shadcn: any element with `aria-invalid="true"` gets destructive treatment automatically.

## Migration checklist

1. Rename: `TextInput` → `Input`.
2. Move `labelText` to a sibling `<label>` (or `<Label htmlFor>`).
3. Move `helperText` to a sibling `<p className="text-xs text-muted-foreground">`.
4. For `invalid` + `invalidText`: add `aria-invalid` on `<Input>` (the built-in destructive border kicks in via CSS), and render the error text in a sibling `<p className="text-xs text-destructive">`.
5. For `warn` + `warnText`: render amber styling by hand — no shadcn primitive for warning.
6. For `readOnly`: keep the prop (native HTML supports it); optionally add `className="bg-muted cursor-not-allowed"` to mimic Carbon's greyed look.
7. For `hideLabel`: drop the prop — either omit the `<label>` and add `aria-label` to `<Input>`, or wrap the `<label>` in `<span className="sr-only">`.
8. For `<PasswordInput>`: switch to `<Input type="password" />`. For the show/hide toggle, compose with a `<Button variant="ghost" size="icon">` (eye / eye-off lucide icons) and a `useState`.
9. For `<NumberInput>` (stepper): use `<Input type="number" />` for native stepper, OR compose with `<Button>`s for a custom stepper UI.
10. For `<SearchInput>`: compose `<Input type="search">` with `<InputGroup>` + `<Search />` icon prefix + optional clear button suffix.
11. For `size`: drop the prop — restyle with `<Input className="h-8" />` (sm) or `<Input className="h-11" />` (lg).
12. For form libraries: works out of the box with `react-hook-form` / `react-final-form` / `formik` because the underlying input is native.
13. For skeleton state: use `<Skeleton className="h-9 w-full" />` from the generic `skeleton` primitive.
