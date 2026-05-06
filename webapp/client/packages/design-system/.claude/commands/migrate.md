# Migrate a UI component wrapper from Carbon to shadcn/ui

Migrate the wrapper named `$ARGUMENTS` from a bare Carbon re-export to a shadcn/ui component
with a Carbon-compatible API, so no consumer changes are required during the transition.

Assumptions (must be true before running this command):

- shadcn is initialised (`components.json` exists at the package root)
- `src/lib/utils.ts` exports the `cn` helper
- A Carbon-compatible base theme is already applied in `globals.css`
- The component folder already exists at `src/components/ui/<name>/` with a `<name>.carbon.tsx`
  that re-exports the Carbon component directly from `@carbon/react`
- `src/index.carbon.ts` and `src/index.shadcn.ts` exist as the package's aggregate entry files

All component folders live in:
`packages/design-system/src/components/ui/`

All shell commands run from:
`packages/design-system/`

---

## File structure for each migrated component

```
src/components/ui/<name>/
  <name>.carbon.tsx   — standalone Carbon re-export (no dependency on our new code)
  <name>.shadcn.tsx   — full shadcn implementation: single <Name> export with all Carbon behaviours
  <name>.types.ts     — shared types: props interface, kind/size unions, kindMap/sizeMap
  <name>.stories.tsx  — Storybook stories comparing Carbon and shadcn versions side by side
```

**Architecture**: `<name>.carbon.tsx` and `<name>.shadcn.tsx` are parallel, independent
implementations. Neither imports from the other. Both are distributed via the package's
`/carbon` and `/shadcn` entry points so consumers can opt in explicitly. `<name>.types.ts` is
the only shared file — both implementations may import from it.

**Source of truth for what must be implemented**: the Carbon component's API (Step 2). Every
behavioural prop in the Carbon component must be replicated in `<name>.shadcn.tsx` using shadcn
and Radix primitives — nothing is silently dropped without explicit justification in the final
report (Step 12).

**Companions** — shadcn components installed alongside the primary one (Step 3) follow the
same architecture. They split into two cases by whether a Carbon equivalent exists. Companion
folders may not exist yet — create them as needed. The table below is the canonical rule per
companion; the steps reference it instead of restating.

### Companion Rules

| Step                | With Carbon counterpart                                                                                                                                                                  | Without Carbon counterpart                                                                                          |
| ------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| 3 (classify)        | Locate Carbon equivalent, read its `.d.ts` (same pattern as Step 2)                                                                                                                      | Mark as shadcn-only primitive                                                                                       |
| 6 (install/folder)  | Move to `<companion>/<companion>.shadcn.tsx` + license header. If folder is new, also create `<companion>.carbon.tsx` re-exporting from `@carbon/react` (with license)                   | Move to `<companion>/<companion>.shadcn.tsx` + license header. No `.carbon.tsx`                                     |
| 7 (implement)       | Rewrite `.shadcn.tsx` as a self-contained component exposing a Carbon-compatible API (license, `cva()` styling matching Carbon tokens, `rounded-none`, no inline comments). Add `.types.ts` only if the prop mapping is non-trivial | Keep shadcn CLI output as the implementation. Ensure license header is present and any radius is `rounded-none`     |
| 9 (export)          | Append to **both** `index.carbon.ts` and `index.shadcn.ts`                                                                                                                               | Append **only** to `index.shadcn.ts`                                                                                |
| 10 (stories)        | `.stories.tsx` with side-by-side Carbon vs shadcn comparison (same pattern as primary)                                                                                                   | shadcn-only `.stories.tsx` demonstrating the primitive's variants/states. No Carbon comparison                      |

Every file must begin with the Camunda license header:

```ts
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
```

This applies to ALL files in the folder, including any file created or overwritten by the
shadcn CLI.

---

## Step 1 — Read the current wrapper

Read `src/components/ui/<name>/<name>.carbon.tsx` to identify the Carbon component being
re-exported.

---

## Step 2 — Read the Carbon TypeScript API

Read the type definitions from:

```
../../node_modules/@carbon/react/es/components/<ComponentName>/<ComponentName>.d.ts
```

(path relative to `packages/design-system/`)

Extract every prop, its type, and all valid values — especially string union literals like `kind`
and `size`. For each prop, note whether it controls visual appearance, behaviour, or both.
Every behavioural prop must be implemented in `<name>.shadcn.tsx` (Step 7).

---

## Step 3 — Fetch the shadcn component API via MCP

Use the shadcn MCP server to identify the shadcn equivalent and get its full props interface
(variants, sizes, defaults). Also search for companion shadcn components that cover Carbon
behaviours the primary shadcn component does not support on its own (e.g. Tooltip for
icon-only buttons, Slot for polymorphic rendering).

For each companion, apply the **Step 3** row of Companion Rules. Install all companions in
Step 6.

---

## Step 4 — Generate the prop mapping and write `<name>.types.ts`

Compare the Carbon and shadcn APIs and produce a typed mapping for each relevant Carbon prop:

- Map every Carbon prop value to its closest shadcn equivalent
- Behavioural Carbon props with no direct shadcn equivalent are still mapped here; they are
  implemented using companion components in Step 7

Write all types and maps to:
`src/components/ui/<name>/<name>.types.ts`

This file contains the Carbon-compatible props interface, kind/size union types, and the `kindMap`/
`sizeMap` constant records. Nothing
type-related is defined inline in the component files — they all import from here.

---

## Step 5 — Extract Carbon's design tokens via subagent

Spawn the `carbon-token-extractor` subagent. It reads the three Carbon SCSS source files
(`_<component>-tokens.scss`, `_vars.scss`, `_<component>.scss`) and returns compact markdown
tables resolving every token to a concrete value. This keeps the raw SCSS out of the main
context.

Use the Agent tool with:

- `subagent_type`: `carbon-token-extractor`
- `description`: `Extract <component> tokens`
- `prompt`: the kebab-case Carbon component name (e.g. `button`, `tag`, `notification`) plus
  any per-component context the agent should know (e.g. "Carbon exposes `kind` values
  primary/secondary/ghost/danger; map all of them.")

The agent returns:

- One color table per variant — columns: state (default/hover/active/disabled/focus) ×
  bg/fg/border for both light and dark themes, all values resolved to hex.
- A single sizing table covering height, width, min-width, max-width, padding-x, padding-y,
  font-size, font-weight per Carbon size.
- A trailing notes list for anything that doesn't fit the tables (box-shadow, opacity,
  conditional borders, unresolved tokens, etc.).

Treat the returned tables as the source of truth for Step 7's `cva()` variants and any new
`globals.css` CSS variables. If the agent flags unresolved tokens in its notes, read those
specific files yourself before proceeding.

---

## Step 6 — Install the shadcn component and companions

Install the primary component and every companion identified in Step 3:

```bash
npx shadcn@latest add <component-name> --overwrite
npx shadcn@latest add <companion> --overwrite
```

The CLI writes each component flat at `src/components/ui/<x>.tsx`. For the primary, apply
the same folder treatment:

```bash
mkdir -p src/components/ui/<x>
mv src/components/ui/<x>.tsx src/components/ui/<x>/<x>.shadcn.tsx
```

Add the Camunda license header at the top of the moved file. No file may remain at the flat
`src/components/ui/<x>.tsx` path after this step.

For each companion, apply the **Step 6** row of Companion Rules.

---

## Step 7 — Write the full implementation to `<name>.shadcn.tsx`

Rewrite `src/components/ui/<name>/<name>.shadcn.tsx` as a single self-contained component.
The file exports one component (`<Name>`) and its props type (`<Name>Props`). The `cva()`
call and any internal helpers are unexported implementation details.

**Styling** — `cva()` variants must match Carbon token values from Step 5:

- **Colors**: prefer CSS variables in `globals.css` (at the package root). For every new CSS
  variable: add the light value (`white-theme`/`g-10`) to `:root`, the dark value (`g-100`)
  to `.dark`, and register it in the `@theme inline` block. Fall back to inline Tailwind
  arbitrary values only if a CSS variable approach would be disproportionately complex. If
  light and dark values are identical, still set the variable in both blocks for clarity.
- **Sizing/spacing**: use Tailwind utilities matching Carbon measurements (e.g. `$spacing-05`
  = `1rem` → `px-4`).
- **Border radius**: always `rounded-none` — Carbon uses 0 radius.
- No inline comments anywhere.

**Behaviour** — `<Name>` implements every behavioural prop from the Carbon API (Step 2) using
the shadcn/Radix companions installed in Step 6. Imports its props interface and maps from
`./<name>.types`. Re-exports `<Name>Props` so type imports continue to work unchanged.

For each companion, apply the **Step 7** row of Companion Rules.

---

## Step 8 — Verify `<name>.carbon.tsx` is untouched

The file already exists as a plain Carbon re-export. Do not modify it. Confirm it still
re-exports only from `@carbon/react` with no local imports.

---

## Step 9 — Update the aggregate entry files

Add the primary component to both entry files in `src/`:

**`src/index.carbon.ts`** — append:

```ts
export * from './components/ui/<name>/<name>.carbon';
```

**`src/index.shadcn.ts`** — append:

```ts
export * from './components/ui/<name>/<name>.shadcn';
```

For each companion, apply the **Step 9** row of Companion Rules.

---

## Step 10 — Create stories

Write `src/components/ui/<name>/<name>.stories.tsx` to import both implementations and show
them side by side. Use a single file — no separate comparison file. Import the carbon version
from `./<name>.carbon` and the shadcn version from `./<name>.shadcn`. Each story should render
both variants so visual differences are immediately visible.

For each companion, apply the **Step 10** row of Companion Rules.

---

## Step 11 — Type-check

```bash
npm run typecheck -w @camunda/design-system
```

Fix any errors. Common issues:

- Carbon props absent from the shadcn component → destructure them out before spreading `rest`
- Polymorphic `as` prop on Carbon components → use `asChild` + `Slot` instead

---

## Step 12 — Report

Summarise:

- The full generated prop mapping (Carbon → shadcn)
- Which Carbon color tokens were mapped to CSS variables vs inline Tailwind values
- Any Carbon props dropped entirely and why
- Any type simplifications made
- Known visual gaps (hover/active states) to address in follow-up iterations
- Companions installed, each classified as **with Carbon counterpart** or **shadcn-only**,
  and the files created for each
