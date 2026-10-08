---
name: design-system-migrator
description: Use when migrating orchestration cluster webapp pages or components from Carbon to the Camunda design system (shadcn), including parallel routes, side-by-side components, and migration tests.
---

# Design System Migrator

Use this skill for the Carbon-to-shadcn migration in
`webapp/client/apps/orchestration-cluster-webapp/`.

The migration is incremental. Carbon and shadcn implementations must coexist until
cutover. Do not replace, move, or delete the Carbon implementation unless explicitly
requested.

For general application architecture, defer to `frontend-feature`. This skill overrides
its Carbon-only component, route, and SCSS guidance during design-system migration.

## Route conventions

- Existing Carbon routes live under `src/routes/_carbon/`. `_carbon` is pathless, so
  existing public URLs do not change.
- Migrated routes live under the existing pathless `src/routes/_shadcn/` tree — **not**
  a new, separate `shadcn/` (non-pathless) tree. It will break at compile time.
- Authenticated routes go under the existing `src/routes/_shadcn/_auth/` shell, which
  already owns the auth guard, `Header`, and session handling — add new leaves there,
  do not recreate any of it.
- If the app being migrated has no DS routes yet, its first migrated route cannot sit
  directly at `_shadcn/_auth/<app>/`: since both `_carbon` and `_shadcn` are pathless,
  that resolves to the same URL as the live Carbon route (`/<app>`) and collides with
  it. Use a temporary, distinguishing leaf name instead — e.g.
  `_shadcn/_auth/<app>-preview/` — reachable at `/<app>-preview`, and rename it to
  `<app>/` (dropping the Carbon route) only at that app's own cutover.
- Mirror the Carbon route hierarchy beneath that leaf.
- Preserve route parameters, validated search parameters, redirects, loaders, titles,
  error behavior, and user-visible behavior unless the migration explicitly changes them.
- Keep route files thin. They wire loaders and page components into TanStack Router;
  feature logic stays in the pod area.
- Do not add Carbon providers or global Carbon styles to shadcn routes. The
  `src/routes/_shadcn/route.tsx` layout owns `ThemeProvider`, design-system styles, and
  the Tailwind stylesheet.
- Do not change the Carbon route while creating its shadcn counterpart.

Example mapping — an app with no DS presence yet (temporary collision-avoiding leaf,
renamed at that app's cutover):

```text
src/routes/_carbon/_auth/operate/route.tsx
src/routes/_shadcn/_auth/operate-preview/route.tsx

/operate
/operate-preview
```

## Component conventions

- Keep existing Carbon components in their current `components/` folder.
- Put migrated components in a sibling `shadcn.components/` folder.
- Keep equivalent names so ownership and comparison remain obvious.
- Import files directly; do not add barrel files.
- Shadcn components use `@camunda/design-system`. Do not import `@carbon/react`, Carbon
  styles, Carbon tokens, or Carbon-specific wrappers.
- Reuse design-system-neutral logic, schemas, queries, and utilities. Do not make shadcn
  components depend on Carbon components or vice versa.
- Do not create compatibility wrappers merely to share markup between both systems.

Example:

```text
src/tasklist/modules/available-tasks/
  components/
    Filters.tsx
    Filters.test.tsx
  shadcn.components/
    Filters.tsx
    Filters.test.tsx
```

## Styling conventions

- Style shadcn routes and components exclusively with Tailwind utility classes.
- Do not create or import SCSS, CSS modules, feature CSS files, or styled-components for
  shadcn routes or components. Translate existing Carbon styles into Tailwind classes
  instead of copying their stylesheets.
- Use `cn` from `#/shared/cn` for conditional classes and when combining classes that may
  conflict.
- The route-level imports of `@camunda/design-system/styles.css` and
  `src/shared/theme/tailwind.css` are infrastructure owned by
  `src/routes/_shadcn/route.tsx`; they are not a pattern for feature-level styling.
- These restrictions apply only to the shadcn implementation. Leave the existing Carbon
  implementation and its styling approach intact.

## Migration workflow

1. Read the Carbon route, components, tests, and styles; record their behavior and visual
   requirements.
2. Add the mirrored route under `src/routes/_shadcn/_auth/` (see Route conventions above
   for the collision-avoiding leaf name when the app has no DS presence yet).
3. Add migrated components under the relevant sibling `shadcn.components/` folder.
4. Preserve behavior and accessibility while replacing Carbon primitives with
   `@camunda/design-system` primitives and translating styles into Tailwind classes.
5. Add or migrate tests beside the shadcn components and under `src/routes/_shadcn/`.
   These paths run in the `shadcn` Vitest browser instance.
6. Leave the Carbon route, components, and tests intact.

## Validation

From `webapp/client/`:

```sh
npm run prettier:format
npm run lint
```

From `webapp/client/apps/orchestration-cluster-webapp/`:

```sh
npm run typecheck
npm run test:unit -- --project shadcn
```
