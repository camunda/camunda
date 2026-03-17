# ADR-0003: Resource-Based Authorization Model

## Status

Accepted

## Context

The previous Management Identity authorization model did not provide sufficient granularity for all
runtime resources. Tasklist and Operate maintained separate, inconsistent access controls, and the
model could not represent fine-grained resource-level permissions (e.g. access to a specific
process definition or user task).

## Decision

Introduce a flexible, resource-based authorization model in Identity. Authorizations link a
principal (user, group, or role) to a resource type and a specific action
(e.g. `PROCESS_DEFINITION:READ`, `USER_TASK:ASSIGN`). These authorizations are enforced uniformly
across UIs, REST APIs, and the Zeebe Engine. Management Identity permissions are migrated to this
new model via the Identity Migration App.

## Consequences

### Positive

- Consistent authorization semantics across all UIs (Operate, Tasklist), all APIs, and the Zeebe
  Engine.
- Least-privilege access: principals are granted only the specific resources and actions they need.
- Extensible: product teams can introduce new resource types and permission types within the shared
  RBAC framework without re-implementing authorization logic.

### Negative

- Additional migration work required when upgrading from pre-8.8 clusters.
- More complex authorization model than the previous role-only approach; requires careful
  configuration by operators.
