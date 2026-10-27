# Optimize retains Camunda 7 identifier naming conventions

**DRI**: BI team

**Status**: Accepted (8.x)

**Purpose**: Explain why Optimize's DTO fields and ES/OS index field names use C7
terminology rather than C8/Zeebe conventions

**Audience**: Engineers and AI coding agents working on the Optimize BE

## Context

Optimize was originally built for Camunda 7 and uses C7 identifier terminology throughout its data
model, ES/OS index field names, and REST API responses.

C7 and C8 use opposite conventions for the terms "Key" and "Id":

|   Term   |                C7 meaning (used in Optimize)                |           C8 / Zeebe meaning            |
|----------|-------------------------------------------------------------|-----------------------------------------|
| `...Key` | Non-unique BPMN process ID string, e.g. `"invoice-process"` | Unique `Long` auto-generated identifier |
| `...Id`  | Unique `Long` auto-generated identifier                     | Non-unique BPMN process ID string       |

For example, `ProcessInstanceDto.processDefinitionKey` holds the `bpmnProcessId` string from
Zeebe, while `ProcessInstanceDto.processDefinitionId` holds the unique `processDefinitionKey`
Long from Zeebe (stored as `String`). This is directly visible at the Zeebe import layer in
`ZeebeProcessInstanceSubEntityImportService` and `ZeebeProcessDefinitionImportService`.

## Decision

**D1. Optimize's DTO field names, ES/OS index field constants, etc. are not renamed.**

The C7 naming is kept as-is across all layers: index mappings, Java DTOs, and REST API responses.
Instead, additional documentation is added to avoid confusion.

## Alternatives considered

- **Full rename to C8 conventions.** Would align Optimize with the rest of the C8 codebase
  but requires migrating all existing customer indices (a customer-visible breaking change) and
  updating the public REST API (breaking change for all API consumers). Not viable without a
  large, explicitly resourced migration effort.
- **Partial renames of any code that does not have a customer impact.** Avoids breaking the REST
  API, export/import format, or requiring a migration. However, it would not completely resolve
  terminology confusion and instead just move the mismatch to different places.

## Consequences

- Engineers and AI coding agents working at the Zeebe import layer must be aware of the C7/C8
  name inversion. Javadoc and inline comments on the import service methods document the mapping at
  the point of translation.
- The root `AGENTS.md` is the canonical instruction entry point for AI coding agents, and
  `optimize/CLAUDE.md` provides module-specific guidance reinforcing this constraint.
- The naming stays until a future migration is explicitly scoped and resourced.

## Source

- [Decision Document](https://docs.google.com/document/d/1SQmYhuhzlVRrymkZKAXPGGO47LDsDf5YQE7ba5eXTEo/edit?usp=sharing)
- [GitHub issue #53375](https://github.com/camunda/camunda/issues/53375)

