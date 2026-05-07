# Project outline

`webapp/client` is an npm workspaces monorepo that holds the unified
orchestration-cluster frontend and the shared libraries that support it.

## Layout

```
webapp/client/
├── apps/
│   └── orchestration-cluster-webapp/
└── packages/
    ├── camunda-api-zod-schemas/
    ├── c8-mocks/
    └── lint-config/
```

## Packages

### `@camunda/camunda-api-zod-schemas`

Published to npm. Provides Zod schemas and TypeScript types for
the Camunda 8 REST API, versioned per release line (8.8 / 8.9 / 8.10).
Consumed by
`@camunda/orchestration-cluster-webapp` and legacy frontend components for type-safe API calls and
runtime validation.

Currently this package is manually written. We plan to automate generation from the OpenAPI spec in the future.

See [Camunda API Zod schemas](./camunda-api-zod-schemas.md) for
installation, usage, and publishing.

### `@camunda/c8-mocks`

Shared mock data fixtures (e.g., current user, license) for
use in webapp tests.

### `@camunda/lint-config`

ESLint and Prettier configuration shared across
the Camunda frontends — also consumed outside this workspace by
`operate/client`, `tasklist/client`, and `identity/client`. Consumers
compose only the eslint variants they need (`base`, `typescript`,
`react`, `testing`, `license`, `tanstack-query`).

## Apps

### `@camunda/orchestration-cluster-webapp`

The unified React webapp that will replace the legacy Operate, Tasklist,
and Admin frontends. See
[Orchestration cluster webapp](./orchestration-cluster-webapp.md) for
the full introduction.
