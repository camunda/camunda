# Extending an existing page

Incremental work on a page that already ships. For greenfield pages
see [Creating a new page](./creating-a-new-page.md); for multi-page
initiatives see [Working on a large feature](./working-on-large-feature.md).

## Overview

You're adding to a page that already ships: a new column, filter,
panel, drill-down, or flow. Two things to settle before writing code:
whether the addition really belongs on this page, and where the new
code lives if it does.

## First, can it be a new route?

Default: yes. If the addition is navigable, has its own state, or
users would deep-link to it, it gets its own route. That can be a nested route or
a search-param-encoded view on the existing one.

Common splits:

- **Tabs**: nested child route, or `?tab=...` when the parent already
  loads the same data.
- **Drill-downs** (list to row, instance to details): nested route,
  e.g. `/_auth/processes/$processKey`.
- **Side panels with shareable state**: search-param flag or nested
  route.

Anything else, justify staying on the existing page in the PR. Full
heuristic in
[Default to a new route](./creating-a-new-page.md#default-to-a-new-route).

## If it stays on the existing page

Build new pieces as modules
([Step 1](./creating-a-new-page.md#step-1-build-the-modules)) and
compose them into the existing page file
([Step 2](./creating-a-new-page.md#step-2-compose-the-page)). No new
abstraction layers, no wrapper-of-wrappers, no parent-feature state
plumbed through new children.

If the addition introduces branching view state (open/closed,
expanded section, selected sub-view), push it to the URL via search
params, validated with Zod
([URL as state](./creating-a-new-page.md#use-the-url-as-state),
[Zod validation](./creating-a-new-page.md#validate-url-inputs-with-zod)).
