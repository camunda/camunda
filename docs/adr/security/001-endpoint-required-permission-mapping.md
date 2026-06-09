# Canonical endpoint → required-permission mapping for the v2 REST API

**DRI**: Identity / Core Features team

**Status**: Proposed (8.10)

**Purpose**: Establish a single, machine-readable source of truth that maps each
v2 REST API operation to the authorization permission(s) it enforces, guard it
against drift and gaps, and make it consumable by tooling (api-test-generator,
docs).

**Audience**: Engineers and AI coding agents working on the v2 REST API,
authorization, and the Zeebe engine.

Relates to: camunda/camunda#54727, camunda/api-test-generator#359.

## Context

There is no single, machine-readable source of truth mapping a REST API endpoint
to the permission it requires. The **resource-type → permission matrix** is
canonical and exported (`AuthorizationResourceType.buildResourcePermissionsMap()`
→ `window.clientConfig.resourcePermissions`), but that only says which
permissions *exist* for a resource — not which permission a given **endpoint**
enforces.

Today the endpoint → permission binding is split and only partially recoverable
from code:

- **Read operations** are declarative in the service layer:
  `service/src/main/java/io/camunda/service/authorization/Authorizations.java`
  defines `RequiredAuthorization` constants (e.g.
  `PROCESS_INSTANCE_READ_AUTHORIZATION`), applied via
  `securityContextProvider.provideSecurityContext(...)`. Machine-extractable.
- **Write operations** are not. The required permission is built inline inside
  engine command processors, e.g.
  `zeebe/engine/.../processinstance/ProcessInstanceCreationHelper.java`:
  `AuthorizationRequest.builder().resourceType(PROCESS_DEFINITION).permissionType(CREATE_PROCESS_INSTANCE)`.
  The `(resourceType, permissionType)` pair exists, but nothing links it back to
  the REST operation. The chain `REST endpoint → BrokerRequest → command intent →
  processor` connects only by naming convention, so reconstructing it requires a
  brittle cross-module scraper that breaks on any refactor.

The REST API reference pages also have no "required permissions" section.

Consumers that need this binding — the api-test-generator (to author 403
deny-path RBAC tests systematically), the docs site, and other tooling — today
have to hand-maintain a duplicate ontology that silently drifts as the API grows.

The repository already has an established, proven pattern for operation-level
metadata: **OpenAPI vendor extensions** on the v2 specs in
`zeebe/gateway-protocol/src/main/proto/v2/` (`x-semantic-establishes`,
`x-eventually-consistent`, `x-added-in-version`), guarded by **Spectral** rules
plus custom JS functions in `spectral-functions/`, backed by JSON registries
(`semantic-kinds.json`). The api-test-generator already consumes the OpenAPI
spec.

## Decision

**D1. The canonical binding lives in the OpenAPI spec as a vendor extension
`x-required-permissions` on every operation.**

The v2 OpenAPI spec is the single source of truth and is already the artifact
external tooling consumes. This reuses the existing `x-semantic-*` machinery
(Spectral + custom functions + JSON registry) rather than inventing a parallel
Java-annotation channel.

```yaml
# Static single permission (the common case)
/process-instances:
  post:
    operationId: createProcessInstance
    x-required-permissions:
      - { resourceType: PROCESS_DEFINITION, permissionType: CREATE_PROCESS_INSTANCE }
```

**D2. The extension is an array; semantics are AND across entries; an empty
array means "no permission required" (public).**

Requiring the key to be present on *every* operation (even when empty)
distinguishes "explicitly public" from "forgot to annotate".

```yaml
# Explicitly public — no authorization enforced
/topology:
  get:
    operationId: getTopology
    x-required-permissions: []
```

**D3. Three entry shapes are supported, to model reality without losing
machine-readability:**

|    Shape    |                             YAML                             |                                                         Meaning                                                         |
|-------------|--------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|
| static      | `{ resourceType: X, permissionType: Y }`                     | A concrete permission. Both values are enum members and a valid pair.                                                   |
| any-of (OR) | `{ anyOf: [ {resourceType, permissionType}, ... ] }`         | At least one of the listed permissions suffices.                                                                        |
| dynamic     | `{ dynamic: true, note: "<how it is resolved at runtime>" }` | Permission is resolved from the request body/path (e.g. `createAuthorization` keys off the `resourceType` in the body). |

The top-level array ANDs its entries. Most operations have exactly one static
entry. `dynamic` entries are not enum-validated for the pair (there is none at
author time) but MUST carry a `note`.

**D4. Validity of `(resourceType, permissionType)` pairs is checked against a
generated registry, not free text.**

`resourceType` must be a member of `AuthorizationResourceType`; `permissionType`
must be a member of `PermissionType`; and the pair must be one the resource type
actually supports (per `AuthorizationResourceType`'s declared permission set).
A generated `resource-permissions.json` (built from the enum, mirroring the
existing `buildResourcePermissionsMap()` export) is the registry the linter
reads — keeping the lint rule decoupled from Java at lint time while staying
honest to the enum.

**D5. Completeness is guarded at lint time (gap guard).**

A custom Spectral function `verifyRequiredPermissionsDeclared` (in
`zeebe/gateway-protocol/spectral-functions/`, severity `error`) fails when any
operation under `paths` lacks `x-required-permissions`, or declares an entry
with an unknown enum value or an unsupported pair. Adding a new endpoint without
a declared binding therefore fails CI by construction.

**D6. Honesty against the engine is guarded by a drift check (drift guard),
staged in two tiers.**

The engine remains the *only* enforcer; the declared mapping must not lie.

- **Tier 1 — static consistency (this PR).** A Java test (`ResourcePermissionsRegistryTest`
  in `zeebe/gateway-rest`) asserts that `resource-permissions.json` — the registry the
  Spectral `verify-required-permissions` rule reads — exactly mirrors
  `AuthorizationResourceType.buildResourcePermissionsMap()`. This keeps the lint-time
  pair-validity guard trustworthy: every declared `(resourceType, permissionType)` pair is
  checked at lint time against a registry that provably cannot drift from the enum, catching
  the most common errors (typos, invalid pairs) cheaply and deterministically. A full
  declaration-vs-enforcement cross-check is not statically feasible today — both read
  (`Authorizations.java`) and write bindings encode the pair in builder lambdas keyed by
  entity/intent rather than by `operationId` — so enforcement honesty is delegated to Tier 2.
- **Tier 2 — runtime enforcement (existing + extended).** The per-resource
  acceptance suite under `qa/acceptance-tests/.../it/auth/` already asserts that
  unauthorized callers receive 403 for each resource. These are the runtime
  ground truth for both read and write endpoints, whose permission is built inline in
  services/processors and is not statically linkable to an operationId today. The ADR
  records the longer-term direction (D7) to make these bindings statically
  extractable so Tier 1 can verify declaration against enforcement directly.

**D7. Direction (non-blocking): centralize the write-side binding.**

Over time, move the `(resourceType, permissionType)` declaration for mutating
operations out of inline processor code into a place keyed by command intent /
operation, so the drift guard can be fully static for writes as well. Not
required to land this issue; tracked as follow-up.

## Consequences

- A documented, machine-readable endpoint → required-permission mapping exists
  for all current v2 endpoints (AC1).
- New endpoints without a declared binding fail CI via the Spectral gap guard
  (AC2).
- A check verifies every declared permission pair is valid against the
  `AuthorizationResourceType` enum (registry-honesty test + lint-time pair
  validation); runtime ITs keep both reads and writes honest (AC3).
- The api-test-generator can read `x-required-permissions` directly from the
  spec to author deny-path tests, eliminating the hand-maintained duplicate.
- Cost: every new operation must declare the extension (one extra block);
  authors get an actionable lint error otherwise.

## Alternatives considered

- **Java `@RequiresPermission` annotation on controllers + exported JSON map.**
  Keeps the binding next to the dispatch code and could be ArchUnit-guarded.
  Rejected as the *primary* channel because the consuming tooling reads the
  OpenAPI spec, not Java; it would still need to be exported into the spec, and
  it does not reuse the existing `x-semantic-*` lint machinery. May still be
  added later as an in-code mirror generated from the spec.
- **Static cross-module scraper** (`endpoint → BrokerRequest → intent →
  processor`). Rejected: relies on naming convention and breaks on refactor —
  exactly the fragility this issue calls out.
- **Document-only (REST reference pages).** Rejected: not machine-readable, not
  guardable, drifts.

