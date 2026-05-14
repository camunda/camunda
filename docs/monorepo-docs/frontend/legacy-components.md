# Legacy components

The legacy frontends still ship independently and will eventually be
integrated into the unified orchestration-cluster webapp.

## Projects

- **`operate/client`**: process monitoring.
- **`tasklist/client`**: user task management.
- **`identity/client`** (also known as Admin): authentication,
  authorization and general cluster admin features.

Each project has its own standards, tooling, and conventions. When
working on them, follow the conventions already established in that
project, not the ones documented in this section.

## Migration

These components will be progressively migrated into
`@camunda/orchestration-cluster-webapp`. For the current status, see
the unification epic
([camunda/camunda#51305](https://github.com/camunda/camunda/issues/51305)).
