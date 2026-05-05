# Migrate a UI component wrapper from Carbon to shadcn/ui

Migrate the wrapper named `$ARGUMENTS` from a bare Carbon re-export to a shadcn/ui component
with a Carbon-compatible API layer, so no consumer changes are required.

Assumptions (must be true before running this command):
- shadcn is initialised (`components.json` exists) and configured to output to `src/components/ui/`
- Tailwind, clsx, and tailwind-merge are installed
- `src/lib/utils.ts` exports the `cn` helper
- A Carbon-compatible base theme is already applied in the global CSS

All wrappers live in:
`webapp/client/apps/orchestration-cluster-webapp/src/components/ui/`

All shell commands run from:
`webapp/client/apps/orchestration-cluster-webapp/`

---

## Step 1 — Read the current wrapper

Read `src/components/ui/<name>.tsx` and identify the Carbon component being re-exported.

---

## Step 2 — Read the Carbon TypeScript API

Read the type definitions from:
```
webapp/client/node_modules/@carbon/react/es/components/<ComponentName>/<ComponentName>.d.ts
```

Extract every prop, its type, and all valid values — especially string union literals like `kind`
and `size`. For each prop, note whether it controls visual appearance, behaviour, or both.
Behavioural props (rendering mode, interactive features) must be implemented in Step 8, not
silently dropped.

---

## Step 3 — Fetch the shadcn component API via MCP

Use the shadcn MCP server to identify the shadcn equivalent and get its full props interface
(variants, sizes, defaults). Also search for companion shadcn components that could implement
Carbon behaviours that the primary shadcn component does not cover on its own. This is used only
for the prop mapping and companion selection — not for the source code.

---

## Step 4 — Generate the prop mapping and write `<name>.types.ts`

Compare the Carbon and shadcn APIs and produce a typed mapping for each relevant Carbon prop:
- Map every Carbon prop value to its closest shadcn equivalent
- Carbon behavioural props with no direct shadcn equivalent: implement them in Step 8 using
  companion components or Radix primitives identified in Step 3
- Carbon props that are pure internal concerns with no user-visible effect: silently drop

Write all types and maps to a dedicated file:
`src/components/ui/<name>.types.ts`

This file contains the Carbon-compatible props interface, kind/size union types, and the
`kindMap`/`sizeMap` constant records. Nothing type-related is defined inline in the component
file — it all imports from here.

---

## Step 5 — Read Carbon's design tokens for this component

Read the following files to extract the component's visual properties:

**Color tokens** (actual hex values per theme):
```
webapp/client/node_modules/@carbon/themes/scss/generated/_<component>-tokens.scss
```

**Structural/sizing variables** (padding, height, font-size, etc.):
```
webapp/client/node_modules/@carbon/styles/scss/components/<component>/_vars.scss
```

**SCSS source** (to understand how tokens are applied to variants):
```
webapp/client/node_modules/@carbon/styles/scss/components/<component>/_<component>.scss
```

From these files, extract for each variant (e.g. primary, secondary, tertiary, ghost, danger):
- Background color (default, hover, active)
- Text color
- Border color and style
- Padding and height
- Font size and weight
- Border radius (Carbon uses 0 — important to enforce)
- Any other visually significant properties

Use the `white-theme` values as the baseline (light mode default).

---

## Step 6 — Install the shadcn component and any required companions

```bash
npx shadcn@latest add <component-name> --overwrite
```

If companion shadcn components were identified in Step 3 as necessary to implement Carbon
behaviours, install them now:

```bash
npx shadcn@latest add <companion> --overwrite
```

---

## Step 7 — Apply Carbon styles to the shadcn component

Rewrite `src/components/ui/<name>.tsx` with:

1. The `cva()` call updated so variants match Carbon token values from Step 5.
2. The internal primitive named `Base<Name>` — never named after the underlying library.
3. No inline comments on variant classes or anywhere else in the file, except the MigrationMap
   block described in Step 8.

**For colors**: prefer extending the global CSS variables in the app's stylesheet over hardcoding
hex values. Add new CSS variables to `src/globals.css` if the base theme variables are not
sufficient. Register them in the `@theme inline` block so they are usable as Tailwind utilities.
Only fall back to inline Tailwind arbitrary values (`bg-[#0f62fe]`) if a CSS variable approach
would be disproportionately complex.

**For sizing/spacing**: use Tailwind utilities that match the Carbon measurements (e.g. Carbon's
`$spacing-05` is `1rem` → `px-4`).

**For border radius**: ensure `rounded-none` is applied — Carbon uses 0 radius everywhere.

---

## Step 8 — Add the Carbon compatibility layer

Add the Carbon-compatible `<Name>` export to the component file. Import all types and maps from
`<name>.types.ts`. Implement every behavioural Carbon prop — use `Base<Name>`, companion
components, and Radix primitives as needed to match Carbon's behaviour.

The file must contain exactly one comment block: the MigrationMap. All other comments must be
absent.

The MigrationMap must appear immediately before the exported component and list every Carbon prop
value mapped to its shadcn equivalent.

Export the Carbon-compatible component as the named export consumers already use, and re-export
`ButtonProps` (or the equivalent type) so type imports continue to work unchanged.

---

## Step 9 — Type-check

```bash
npm run typecheck
```

Fix any errors. Common issues:
- Carbon props absent from the shadcn component → destructure them out before spreading `rest`
- Polymorphic `as` prop on Carbon components → use `asChild` + `Slot` instead
- Missing Tailwind content paths → verify `tailwind.config.*` includes `src/**/*.tsx`

---

## Step 10 — Report

Summarise:
- The full generated prop mapping (Carbon → shadcn)
- Which Carbon color tokens were mapped to global CSS variables vs inline Tailwind values
- Any Carbon props dropped entirely and why
- Any type simplifications made
- Known visual gaps (hover/active states, dark theme) to address in follow-up iterations
