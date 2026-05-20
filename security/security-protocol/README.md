# security-protocol

Protocol-level authorization enums used by the Zeebe engine, log stream, and RocksDB state machine.

## Why this module exists

The Zeebe engine serializes authorization data into SBE-encoded records that are written to the log stream and persisted in RocksDB. The enums in this module are part of that serialization schema. Reordering, renaming, or removing an enum constant without a coordinated migration breaks log replay and RocksDB reads on upgrade — producing silent data corruption or unrecoverable partitions.

This module therefore has a strict stability contract that is different from the rest of the authorization stack.

## What lives here

|             Class              |                                       Purpose                                        |
|--------------------------------|--------------------------------------------------------------------------------------|
| `AuthorizationResourceType`    | All resource types that can be authorized (process definitions, decisions, users, …) |
| `PermissionType`               | All permission types that can be granted on a resource                               |
| `AuthorizationOwnerType`       | Types of principals that can hold authorizations (user, role, group, …)              |
| `AuthorizationResourceMatcher` | How a resource grant is matched (any, specific id, property)                         |
| `AuthorizationScope`           | Scope/context of an authorization                                                    |
| `DefaultRole`                  | Built-in roles shipped with the platform                                             |
| `EntityType`                   | Classification of identity entities                                                  |

## The layered enum rule

**These enums are for the engine layer only.** All code above the engine — Service, Search, Exporter, and Persistence layers — uses the canonical enums from CSL (`io.camunda.security.api.model.authz`). The `AuthzModelMapper` in the `service` module is the single translation point between the two sets.

```
Service / Search / Exporter / Persistence   →  use CSL enums
─────────────────────────────────────────────────────────────
            AuthzModelMapper (translation boundary)
─────────────────────────────────────────────────────────────
Zeebe engine / log stream / RocksDB          →  use enums from this module
```

CSL is the canonical catalogue of all possible values. Hosts (including this module) mirror the values they need and map via `AuthzModelMapper`. See [CSL ADR-0016](https://github.com/camunda/camunda-security-library/blob/main/docs/adr/0016-authz-enum-ownership-and-layered-usage.md) for the full decision record.

## Rules for contributors

**Do not remove or rename existing enum constants.** They are part of the SBE/RocksDB serialization schema. Removal or renaming requires a multi-step migration and a revapi `ignored-changes.json` entry. If a constant is no longer semantically meaningful, deprecate it first.

**Do not reorder enum constants.** Ordinal-based serialization depends on declaration order.

**Adding a new constant requires updating both hierarchies.** When you add a new `AuthorizationResourceType` or `PermissionType` value here, you must also:

1. Add the value to the corresponding CSL enum (`camunda-security-library`, `api/src/main/java/io/camunda/security/api/model/authz/`).
2. Update the SBE schema in `zeebe/protocol/`.
3. Add the mapping in `AuthzModelMapper`.
4. Assert completeness in `AuthzModelMapperTest` (every constant in this module must have a mapping).

