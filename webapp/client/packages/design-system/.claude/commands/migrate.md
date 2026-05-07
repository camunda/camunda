# Generate a Carbon → shadcn prop adapter

Generate `<name>.adapter.tsx` for the wrapper named `$ARGUMENTS`. With no argument
(or `$ARGUMENTS` equal to `all`), generate adapters for every wrapper in `MAPPING.md`
that has a shadcn equivalent and is missing an adapter file.

The adapter takes Carbon-shaped props and renders the corresponding shadcn primitive,
translating value enums (e.g. `kind` → `variant`, `size` → `size`) and dropping props
with no shadcn equivalent (with a dev-only `console.warn`). Consumers keep importing
the same identifier names (e.g. `Tag`, `Modal`, `ActionableNotification`); switching
the package from Carbon to shadcn is a one-line change in `src/index.ts`.

This skill produces ONLY the adapter file and updates `src/index.shadcn.ts`. It does
NOT touch `<name>.carbon.tsx`, `<name>.shadcn.tsx`, stories, docs, or `globals.css`.

## Assumptions

- `MAPPING.md` is up to date at `src/components/ui/MAPPING.md`
- Each component folder already has `<name>.carbon.tsx` (Carbon re-export) and
  `<name>.shadcn.tsx` (canonical shadcn primitive or a re-export of one from a sibling
  primitive folder)
- `src/index.carbon.ts` and `src/index.shadcn.ts` exist
- `@carbon/react` types live at `../../node_modules/@carbon/react/es/components/<ComponentName>/<ComponentName>.d.ts`
  (path relative to `packages/design-system/`)

All paths in this document are relative to `packages/design-system/`.

## File structure after running

```
src/components/ui/<name>/
  <name>.carbon.tsx      — UNCHANGED bare Carbon re-export
  <name>.shadcn.tsx      — UNCHANGED canonical shadcn primitive (or re-export)
  <name>.adapter.tsx     — NEW: Carbon-API surface, renders shadcn underneath
  <name>.stories.tsx     — UNCHANGED
  <name>.docs.mdx        — UNCHANGED
  <name>.migration.mdx   — UNCHANGED
```

`src/index.shadcn.ts` after the sweep mirrors `src/index.carbon.ts`'s identifier
names: every Carbon wrapper resolves through the adapter (or directly through
`<name>.carbon` for the 13 rows in MAPPING.md's "No shadcn equivalent" table).

Every new file begins with the Camunda license header:

```ts
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
```

---

## Step 1 — Resolve the target list

If `$ARGUMENTS` is empty, equal to `all`, or `--all`:

- Read `src/components/ui/MAPPING.md` and collect every wrapper name from the
  "Direct matches" and "Conceptual matches" tables that does NOT yet have a
  `<name>.adapter.tsx` file at `src/components/ui/<name>/`.
- Skip every row in the "No shadcn equivalent" table (those are Carbon-only by
  design — `index.shadcn.ts` re-exports `<name>.carbon` for them in Step 5).

Otherwise process the single wrapper named in `$ARGUMENTS`. Verify it appears in
either the "Direct matches" or "Conceptual matches" table — refuse to generate an
adapter for a "No shadcn equivalent" row.

---

## Step 2 — Read the Carbon API surface

For each target:

1. Read `src/components/ui/<name>/<name>.carbon.tsx` to enumerate the identifiers
   the adapter must mirror — single (`Tag`) or compound (`Tabs, TabList, TabPanel,
   TabPanels, Tab`) — and the prop type names re-exported.
2. For each identifier, read its Carbon type definition at:

   ```
   ../../node_modules/@carbon/react/es/components/<ComponentName>/<ComponentName>.d.ts
   ```

   Some components live under a different folder than their export name (e.g.
   `ModalHeader` → `node_modules/@carbon/react/es/components/ComposedModal/ModalHeader.d.ts`).
   If `<ExportName>.d.ts` is not present in `components/<ExportName>/`, search
   `components/` for the file.

   Extract every prop, its type, valid string-union literals, and whether it is
   marked `@deprecated`. Deprecated props still need adapter handling — drop with
   warning, do not error.

---

## Step 3 — Read the shadcn primitive

Read `src/components/ui/<name>/<name>.shadcn.tsx`. There are two cases:

- The file IS the shadcn primitive (e.g. `button.shadcn.tsx`, `dialog.shadcn.tsx`).
  Use those exports directly.
- The file re-exports from a sibling primitive folder (e.g. `tag.shadcn.tsx`
  re-exports `Badge` from `../badge/badge.shadcn`). Read the sibling file to learn
  the primitive's API.

Note the primitive's prop names, variant/size enums, and any required structural
composition (e.g. `<Dialog>` requires a `<DialogContent>` child).

If MAPPING.md's row for this wrapper indicates a conditional primitive choice
(e.g. "Modal: `danger` → AlertDialog else Dialog"), read both primitives.

---

## Step 4 — Write `<name>.adapter.tsx`

The adapter file:

- Begins with the Camunda license header.
- Imports the shadcn primitive(s) from `./<name>.shadcn` (or sibling primitive folder
  if `<name>.shadcn.tsx` is itself a re-export).
- Imports Carbon prop types from `@carbon/react` as `type` imports for the public
  prop-type re-exports.
- Exports the SAME identifiers as `<name>.carbon.tsx` (e.g. `Tag`, or
  `Tabs`+`TabList`+`TabPanel`+`TabPanels`+`Tab`).
- Re-exports the Carbon prop type as the public type:
  `export type TagProps = CarbonTagProps;` so consumer type imports keep compiling.

### Prop translation

For each Carbon prop value with a closest shadcn equivalent, define a small inline
lookup constant typed against both APIs:

```ts
const KIND_TO_VARIANT: Record<NonNullable<CarbonButtonProps['kind']>, ButtonShadcnVariant> = {
  primary: 'default',
  secondary: 'secondary',
  tertiary: 'outline',
  ghost: 'ghost',
  danger: 'destructive',
  'danger--tertiary': 'destructive',
  'danger--ghost': 'destructive',
};
```

Lookup constants are inline implementation detail — do not export them.

### Lossy props

For Carbon props with no shadcn equivalent, drop them and emit a dev-only warning
using the shared helper:

```ts
import {warnDroppedProps} from '../../../lib/utils';

function MyComponent(props: MyProps) {
  const {supportedA, unsupportedB, unsupportedC, ...rest} = props;
  warnDroppedProps('MyComponent', {unsupportedB, unsupportedC});
  // ...
}
```

`warnDroppedProps(component, dropped)` lives in `src/lib/utils.ts`. It is a no-op
in production (gated by `process.env['NODE_ENV']`) and emits a single
`console.warn` per render listing the dropped props. Call it in the adapter
function body — running once per render is acceptable for dev-only warnings.

**Use relative imports, not `@/lib/utils`.** The `@/*` alias only resolves under
the design-system's own `tsconfig.json`; consumers (Operate, Tasklist) follow
package source files at typecheck time and cannot resolve the alias. From any
`src/components/ui/<name>/<name>.adapter.tsx`, the correct path is
`../../../lib/utils`.

Passing destructured props through `warnDroppedProps` also satisfies the
`noUnusedLocals` TypeScript rule: every Carbon prop pulled out of `props` is either
used by the adapter or surfaces in the dropped-props object, so nothing is silently
unused.

Do NOT warn for deprecated props that map cleanly to a shadcn equivalent (e.g.
Carbon's `filter`/`onClose` → render Badge with a close button is a deliberate
mapping, not a drop). Only warn when the prop is genuinely unsupported.

### Behavioural mismatches

Some adapters can't be a thin function — they require composition logic:

- **Carbon monolithic → shadcn compound** (e.g. `<Modal modalHeading="..." primaryButtonText="...">`
  → render `<Dialog><DialogContent><DialogHeader><DialogTitle>...`): the adapter
  internally builds the compound structure from the Carbon string props.
- **Conditional primitive** (e.g. Modal `danger=true` → render `AlertDialog` instead
  of `Dialog`): branch inside the adapter.
- **Pair-by-index → pair-by-value** (Carbon Tabs): synthesise a `value` from each
  child's index and thread it through both the trigger list and the panels list.

Encode the logic in the adapter; do not push it back to consumers.

### Style and conventions

- Use `forwardRef` only when the underlying shadcn primitive accepts a ref.
- No inline doc comments inside the adapter body. The MAPPING.md row is the
  documentation; the adapter is the implementation.
- A short header comment block (1–3 lines under the license) is allowed only if it
  states a non-obvious WHY (e.g. "Renders AlertDialog when `danger` is true").
- No `.types.ts` file. Keep all types inline in the adapter.
- No `MigrationMap` re-export, no `kindMap`/`sizeMap` exported names. Prop maps are
  unexported `const`s.

---

## Step 5 — Update `src/index.shadcn.ts`

After all targets are processed, rewrite `src/index.shadcn.ts` so it exposes the
exact same identifier names as `src/index.carbon.ts`. Each entry follows one of
two patterns:

```ts
// Wrapper has a shadcn equivalent → re-export from the adapter
export * from './components/ui/<name>/<name>.adapter';

// Wrapper has no shadcn equivalent (per MAPPING.md "No shadcn equivalent" table)
// → fall back to the Carbon implementation so the export surface stays stable
export * from './components/ui/<name>/<name>.carbon';
```

Order the entries to match `src/index.carbon.ts` (alphabetical, with the
`ThemeProvider` re-export at the top).

If the current `src/index.shadcn.ts` exports raw shadcn primitives under shadcn
names (e.g. `Badge`, `Dialog`), replace those entries — the adapter mirror is now
the only contract.

---

## Step 6 — Type-check

```bash
npm run typecheck -w @camunda/design-system
```

Common failures and fixes:

- Carbon prop absent from the shadcn primitive → destructure it out (so it goes
  into a `dropped` object the warning helper inspects) before spreading `rest`.
- Polymorphic `as` prop on a Carbon component → use `asChild` + `Slot` if the
  shadcn primitive supports it; otherwise drop with a warning.
- `forwardRef` ref-type mismatch → match the shadcn primitive's ref element type
  (often different from Carbon's, e.g. `<button>` vs `<div>`); document this in
  MAPPING.md if it changes the consumer-facing ref shape.

---

## Step 7 — Report

For each adapter generated, report:

- The file path and a one-line summary of what it renders.
- Carbon → shadcn prop value translations (e.g. `kind: 'primary' → variant: 'default'`).
- Carbon props dropped with a dev-only warning, and the rationale.
- Whether the adapter is a single component or compound (list every exported
  identifier).
- Any behavioural caveats consumers should know about (e.g. "passes `open`/
  `onOpenChange` instead of `open`/`onRequestClose` to its children — controlled
  state still works because the adapter translates").

When `$ARGUMENTS` is `all`, group the report by component and end with a count of
adapters created vs. skipped.
