# Authorization Enum Ownership and Layered Usage

## Rule

Use the **CSL enums** (`io.camunda.security.api.model.authz.ResourceType` and `io.camunda.security.api.model.authz.PermissionType`) in all code outside the Zeebe engine layer.

Do **not** use `io.camunda.zeebe.protocol.record.value.AuthorizationResourceType` or the protocol-level `PermissionType` in Service, Search, Exporter, or Persistence layer code.

## Which layers this covers

|                      Layer                      |         Use CSL enums?         |
|-------------------------------------------------|--------------------------------|
| Service (`*Services`)                           | Yes                            |
| Search / Query (`*DbReader`, REST handlers)     | Yes                            |
| Exporter (ES / OS / RDBMS exporters)            | Yes                            |
| Persistence (`*Writer`, `*Store`, RDBMS)        | Yes                            |
| Zeebe engine (broker, processor, state-machine) | No — keep Zeebe protocol enums |

## Why

CSL owns the canonical catalogue of all possible `ResourceType` and `PermissionType` values. Hub uses this catalogue to let operators maintain authorization rules. If engine-layer protocol enums were replaced directly, RocksDB serialization and the log-stream schema would break across rolling upgrades (SBE-encoded records are sensitive to enum reordering/renaming).

See [CSL ADR-0016](https://github.com/camunda/camunda-security-library/blob/main/docs/adr/0016-authz-enum-ownership-and-layered-usage.md) for the full decision record.

## The translation boundary

`AuthzModelMapper` (in the `service` module) is the single point that translates between Zeebe protocol enum values and CSL enum values. It sits at the seam between the engine layer and the layers above.

- If you are writing code **above** `AuthzModelMapper` (Service, Search, Exporter, Persistence): use CSL enums.
- If you are writing code **below** it (engine, processor, log-stream): use Zeebe protocol enums.

## Adding a new ResourceType or PermissionType

When introducing a new value, you must update **both** enum hierarchies:

1. Add the value to the CSL enum in `camunda-security-library` (`api/src/main/java/io/camunda/security/api/model/authz/`).
2. Add the value to the Zeebe protocol enum in `zeebe/protocol/` (including the SBE schema).
3. Add the mapping to `AuthzModelMapper`.
4. Add a test assertion in `AuthzModelMapperTest` that verifies every Zeebe protocol enum constant has a corresponding CSL mapping (completeness check).

Missing step 3 or 4 will cause silent failures at runtime when the engine produces a value the mapper does not know.
