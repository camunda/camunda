---
name: orchestration-cluster-migration-conventions
description: Use when migrating a Carbon route or component in webapp/client/apps/orchestration-cluster-webapp/ to @camunda/design-system, or adding files under src/routes/_shadcn/ or a shadcn.components/ folder. Covers the pathless _carbon/_shadcn routes, the -preview leaf, sibling shadcn.components/ folders, Tailwind-only styling, and the matching Vitest projects. Component mapping is done by the Design System plugin's migrate skill.
---

# Orchestration Cluster Migration Conventions

Use this skill when moving `webapp/client/apps/orchestration-cluster-webapp/` from Carbon
to `@camunda/design-system`.

The migration is incremental. Carbon and `@camunda/design-system` implementations must
coexist until cutover. Do not replace, move, or delete the Carbon implementation unless
explicitly requested.

For general application architecture, defer to `frontend-feature`. This skill overrides
its Carbon-only component, route, and SCSS guidance during design-system migration.

## Component mapping

This skill does not map Carbon components. Use the `/camunda-design-system:migrate` skill
from the Camunda Design System Claude Code plugin for that. It classifies each Carbon
import as REMAP, WRAP, or FLAG and runs a visual check.

- That skill edits the file it is given. First create the copy under `shadcn.components/`
  or `src/routes/_shadcn/` (see below), then run `/camunda-design-system:migrate <copy>`.
  Never run it on the Carbon original.
- WRAP output re-exports `Layer` or `Theme` from `@carbon/react`. That is not allowed in
  `shadcn.components/` or `src/routes/_shadcn/`; handle those symbols as FLAG.
- Decline its consumer-setup patch (`apply-setup.sh`). It edits the app entry file and
  wraps `<App />` in `C4Provider`; in this app the design-system setup lives in
  `src/routes/_shadcn/route.tsx`.
- Never import `@camunda/design-system/carbon-compat`. It is deprecated.

If the plugin is not installed, follow the
[Design System README](https://github.com/camunda/design-system#claude-code-plugin): run
`/plugins`, add the marketplace `camunda/design-system`, then install the
`camunda-design-system` plugin.

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
- Do not add Carbon providers or global Carbon styles to `_shadcn` routes. The
  `src/routes/_shadcn/route.tsx` layout owns `ThemeProvider`, design-system styles, and
  the Tailwind stylesheet.
- Do not change the Carbon route while creating its `_shadcn` counterpart.

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
- Components in `shadcn.components/` use `@camunda/design-system`. Do not import
  `@carbon/react`, Carbon styles, Carbon tokens, or Carbon-specific wrappers.
- Reuse design-system-neutral logic, schemas, queries, and utilities. Do not make
  `shadcn.components/` depend on Carbon components or vice versa.
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

- Style `_shadcn` routes and `shadcn.components/` exclusively with Tailwind utility
  classes.
- Do not create or import SCSS, CSS modules, feature CSS files, or styled-components for
  them. Translate existing Carbon styles into Tailwind classes instead of copying their
  stylesheets.
- Use `cn` from `#/shared/cn` for conditional classes and when combining classes that may
  conflict.
- The route-level imports of `@camunda/design-system/styles.css` and
  `src/shared/theme/tailwind.css` are infrastructure owned by
  `src/routes/_shadcn/route.tsx`; they are not a pattern for feature-level styling.
- These restrictions apply only to the `@camunda/design-system` implementation. Leave the
  existing Carbon implementation and its styling approach intact.

## Tests

Add or migrate tests beside the `shadcn.components/` files and under
`src/routes/_shadcn/`. Under `src/operate/`, `src/shared/`, and `src/routes/` these paths
run in the `operate:shadcn` and `shared:shadcn` Vitest projects; `src/admin/` and
`src/tasklist/` run all their tests with the design-system setup in the `admin` and
`tasklist` projects (see `vite.config.ts`). Leave the Carbon tests intact.

## Validation

From `webapp/client/`:

```sh
npm run prettier:format
npm run lint
```

From `webapp/client/apps/orchestration-cluster-webapp/`:

```sh
npm run typecheck
npm run test:unit -- --project '*:shadcn'
# for src/admin/ or src/tasklist/: --project admin or --project tasklist
```
