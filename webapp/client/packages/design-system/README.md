<!--
Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
one or more contributor license agreements. See the NOTICE file distributed
with this work for additional information regarding copyright ownership.
Licensed under the Camunda License 1.0. You may not use this file
except in compliance with the Camunda License 1.0.
-->

# @camunda/design-system

Camunda's design system package. Hosts the migration from Carbon to shadcn/ui:
each component lives in `src/components/ui/<name>/` with three implementations
side-by-side — Carbon re-export, shadcn primitive, and a Carbon-shaped adapter
that lets consumers stay on Carbon's prop API while shadcn renders underneath.

## Switching between Carbon and shadcn

`src/index.ts` is the package's single entry point. To switch the entire design
system from Carbon to shadcn, change one line:

```ts
// Use Carbon (default)
export * from './index.carbon';

// Use shadcn (Carbon-shaped adapters render shadcn primitives)
export * from './index.shadcn';
```

`index.shadcn.ts` exposes the same identifier names as `index.carbon.ts`, so
consumers' imports keep compiling. Carbon props that have no shadcn equivalent
are dropped at runtime with a dev-only `console.warn` (see
`src/lib/utils.ts#warnDroppedProps`).

## Generating new adapters

Use the `/migrate` skill (see `.claude/commands/migrate.md`). With no argument,
it sweeps every wrapper in `src/components/ui/MAPPING.md` that's missing an
`<name>.adapter.tsx` file. Each adapter:

- Accepts Carbon-shaped props.
- Renders the shadcn primitive from `<name>.shadcn.tsx`.
- Translates value enums (`kind` → `variant`, etc.) via inline `Record<...>`
  lookups.
- Drops unsupported Carbon props with `warnDroppedProps`.

## TODO

- [ ] **Ship a built artifact instead of source files.** Today consumers'
  TypeScript follows the package's source tree at typecheck time. That works
  with relative imports inside `src/`, but the package can't use the `@/*`
  path alias — it doesn't resolve from the consumer side. A proper build step
  (e.g. `tsc --outDir dist/` + `package.json#exports` pointing at `dist/`)
  would let internals use the alias freely and decouple consumers from the
  internal layout. The `@/*` alias mapping is still defined in
  `tsconfig.json` so Storybook keeps working, but no runtime code uses it.
- [ ] **Pre-build CSS so consumers don't need Tailwind.** Because the package
  ships source, each consumer (e.g. `operate/client`) must run Tailwind
  itself — pointing `@source` at the design-system's `.tsx` files and hosting
  `globals.css` inside a `tailwindcss` import context so that `@theme inline`
  and `@custom-variant` directives are processed. A build step that
  pre-generates the utility CSS (and resolves the theme tokens) would let
  consumers drop their Tailwind dependency entirely and simply
  `@import '@camunda/design-system/dist/styles.css'`. This also eliminates
  Tailwind's preflight leaking into Carbon-based pages.
- [ ] **Remove `.cds--structured-list-tbody` shim from `globals.css`.** A
  `display: table-row-group` rule is currently patched onto the raw Carbon
  class name in `globals.css` so that `operate/client`'s
  `modules/components/StructuredList/index.tsx` (which emits
  `<div className="cds--structured-list-tbody">` directly) works in shadcn
  mode without Carbon's CSS. The proper fix is to update that wrapper to use
  the `StructuredListBody` adapter — then this global shim can be deleted.
  Tracked here because the shim is a fragile workaround: any other raw Carbon
  class name used directly outside the adapter will need a similar patch until
  all consumers go through the adapters.
