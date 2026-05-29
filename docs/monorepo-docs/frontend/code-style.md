# Code style

General style conventions for the orchestration-cluster webapp
codebase. Linting and formatting are enforced by the shared
`@camunda/lint-config` package.

## YAGNI

Don't build what you don't need yet. No abstractions for hypothetical
future use. Wait until a real requirement forces the shape. Three
similar lines beat a premature wrapper.

## Naming

- **Booleans**: prefix with `is` or `has` (e.g. `isLoading`, `isVisible`, `hasIncident`).
- **Constants**: `SCREAMING_SNAKE_CASE` (e.g. `MAX_RETRY_COUNT`).
- **Components**: `PascalCase` (e.g. `DashboardHeader`).
- **Pages**: `PascalCase` + `Page` suffix (e.g. `DashboardPage`). Co-located styles and tests use the same name (`DashboardPage.module.scss`, `DashboardPage.test.tsx`).

## Exports

Use a single export block at the end of the file. Do not use inline
`export` on declarations.

```ts
// good
function parseInput(raw: string) { ... }
function formatOutput(data: Data) { ... }

export {parseInput, formatOutput};

// avoid
export function parseInput(raw: string) { ... }
export function formatOutput(data: Data) { ... }
```

## Comments

Avoid comments. The code should be readable enough to explain itself.
When a comment is necessary, explain why, not how.

```ts
// good
// The API returns dates as Unix seconds, not milliseconds.
const timestamp = raw * 1000;

// bad
// Multiply raw by 1000.
const timestamp = raw * 1000;
```
