# Working on a large feature

How to ship a feature that spans multiple pages, modules, or weeks of
work. The per-PR mechanics still follow
[Creating a new page](./creating-a-new-page.md) and
[Extending an existing page](./extending-an-existing-page.md); this
page covers how to break the work down and keep unfinished features
off for users.

## Overview

Large features merged as a single PR are hard to review for both
humans and agents, risky to merge, and delay value delivery. Split the
work into smaller PRs that each stand on their own.

## Split into smaller PRs

Each PR should be a meaningful, self-contained unit, not "part 1 of 5"
but a slice that adds value or lays necessary groundwork. It should
build, pass tests, and be reviewable in isolation.

How to slice:

- **By layer**: schemas/types first, then modules, then page, then
  route wiring.
- **By user-visible unit**: one filter, one column, one panel, one
  flow per PR.
- **By dependency order**: shared modules before consumers.

When in doubt, err on the side of smaller. A PR that is too small is
still easy to review. A PR that is too large is not.

## Feature flags

When the feature is not ready to be enabled for users but code needs
to merge to `main`, gate it behind a feature flag.

### Creating a flag

Export a boolean `const` from
`src/shared/feature-flags.ts`. Use `SCREAMING_SNAKE_CASE` and
default to `false`.

```ts
// src/shared/feature-flags.ts
export const ENABLE_PROCESS_MIGRATION = false;
```

### Using a flag

Gate at the highest possible level: route registration, page
component, or navigation item. Do not scatter flag checks deep inside
modules.

```tsx
import { ENABLE_PROCESS_MIGRATION } from "#/shared/feature-flags";

function ProcessActions() {
  return (
    <>
      {ENABLE_PROCESS_MIGRATION && <MigrateButton />}
    </>
  );
}
```

### Large refactors behind a flag

When a feature rewrites a significant portion of an existing page or
module, copy the affected code and build the new version alongside the
old one. Toggle between them with the feature flag. This keeps PRs
small and focused: reviewers see only the new code, not a giant diff
of interleaved changes. The cleanup PR that removes the flag also
deletes the old copy.

### Removing a flag

Once the feature ships and is validated, remove the flag in a
dedicated cleanup PR:

1. Set the flag to `true` and verify everything works.
2. Remove the `const` from `feature-flags.ts`.
3. Remove all conditional branches that read it.
4. Delete any dead code that was behind the negative branch.

Do not leave flags lingering. If removal is deferred, file a tracking
issue and link it from a comment next to the flag.

## Checklist

Before opening each PR in a large feature:

- [ ] PR is a self-contained slice, not a partial dump.
- [ ] Feature flag added in `src/shared/feature-flags.ts` if the
      feature is not ready to enable.
- [ ] Flag gating lives at route or page level, not deep in modules.
- [ ] No leftover flags without a tracking issue for removal.
- [ ] PR follows the per-PR checklist from
      [Creating a new page](./creating-a-new-page.md#checklist).
