# Remove internal numeric key fields from Identity entity filter builders

**DRI**: Identity team

**Status**: Accepted (8.10)

**Purpose**: Record the decision to drop internal numeric `key` filter fields from
the Identity entity filter builders (`UserFilter`, `GroupFilter`, `TenantFilter`,
`MappingRuleFilter`) in the search domain, and to fix the boundary of that cleanup
so future filters follow the same rule.

**Audience**: Engineers and AI coding agents working on the search domain
(`search/`, `db/`) and the Identity entity REST/query APIs.

Relates to: camunda/camunda#41657 (parent), camunda/camunda#51088,
camunda/camunda#51089, camunda/camunda#51090. Follow-up: camunda/camunda#51091.

## Context

Identity entities (User, Group, Tenant, MappingRule) each carry two identifiers:

1. An internal numeric `key` — a database-sequence value that is an implementation
   detail of how the entity is stored.
2. A user-provided string ID — `username`, `groupId`, `tenantId`, `mappingRuleId`
   — which is the canonical external identifier.

The search-domain filter builders for these entities previously exposed a method to filter by
the internal numeric key (e.g. `UserFilter.Builder.key(Long)` mapping to the
`key` term of the user index). These key filters were:

- **Never reachable from the REST API** — no v2 search endpoint accepts a numeric
  key as a filter parameter for these entities.
- **Never populated from external requests** — no production service code set the
  field; the only consumers were internal/test code (e.g.
  `UserDbReader.findOne(long)`).
- **Contradictory to the design intent** that string IDs are the canonical external
  identifiers for Identity entities.

`RoleFilter` set the precedent: its `roleKey` had already been removed, leaving
`roleId` (string) as the only filter identifier. The remaining four entities were
inconsistent with it.

## Decision

**D1. Remove the numeric key filter field from `UserFilter`, `GroupFilter`,
`TenantFilter`, and `MappingRuleFilter`.**

This includes the record field, the builder method, the `toBuilder()` copy, the
query transformer mapping (`... -> term(KEY, ...)`), and the RDBMS reader/mapper
plumbing that consumed it. `UserDbReader.findOne(long)` is deleted as the sole
internal caller with zero non-test production callers.

Identity entities are filtered exclusively by their business string IDs.

Implemented and merged:

- `UserFilter` / `GroupFilter` — camunda/camunda#51088 (PR camunda/camunda#54110).
- `TenantFilter` / `MappingRuleFilter` — camunda/camunda#51089.

## Explicitly out of scope

- **`AuthorizationFilter.authorizationKey`** stays. Authorizations have no string-ID
  equivalent; the numeric key *is* the externally-exposed primary identifier
  (`/authorizations/{authorizationKey}`). It is a legitimate REST-facing key, not an
  internal one.
- **Sort fields** (`UserSort.key()`, `GroupSort.groupKey()`, `TenantSort.tenantKey()`,
  `MappingRuleSort.mappingRuleKey()`) are a separate concern and are not touched here.
  The entity indexes retain their `key` field and key-based *sorting* continues to
  work. Most sort keys are already hidden from the OpenAPI spec, but the Tenant search
  endpoint still exposes `key` as a sort option — reconciling that asymmetry is tracked
  separately in camunda/camunda#51091 and requires a breaking-change decision.

## Consequences

### Positive

- The filter builder API accurately reflects what can be filtered on externally.
- Reduces the internal API surface and removes a misleading, unreachable parameter.
- Aligns all Identity entity filters with the `RoleFilter` precedent, giving future
  filters a single clear rule: filter by business string ID, not internal key.

### Negative

- Internal and test code that used key-based filters had to be updated. This is
  accepted, one-time churn with no external impact.

## Alternatives considered

- **Keep the key filters "just in case".** Rejected: they were dead, unreachable
  surface that diverged from the string-ID design and confused readers of the API.
- **Also remove the key sort fields in the same change.** Rejected: the Tenant sort
  `key` is live in the OpenAPI spec, so removing it is a breaking change that needs
  its own discussion (camunda/camunda#51091) rather than being bundled here.

