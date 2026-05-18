# Optimize Module — AI Agent Notes

## C7/C8 Terminology Warning

**Do not confuse `...Id` fields with `...Key` fields, they hold opposite types of values to what C8
developers may expect. Do not rename Optimize's DTO fields or ES/OS index field constants to match
C8 naming conventions.**

Optimize was built for Camunda 7 and retains C7 identifier terminology throughout its backend. The
C8 (Zeebe) world **inverts** the meaning of "Key" and "Id":

|   Term   |                C7 meaning (used in Optimize)                |        C8 / Zeebe meaning         |
|----------|-------------------------------------------------------------|-----------------------------------|
| `...Key` | Non-unique BPMN process ID string, e.g. `"invoice-process"` | Unique `Long` identifier          |
| `...Id`  | Unique `Long` identifier (often stored as `String`)         | Non-unique BPMN process ID string |

This naming is **intentionally kept** and must not be changed. The decision is documented in
[`docs/adr/001-c7-naming-conventions.md`](docs/adr/001-c7-naming-conventions.md).

This convention applies across the entire Optimize codebase, not just process definitions. Any
field whose name ends in `...Id` holds a unique identifier, and any field whose name ends in
`...Key` holds a non-unique string — regardless of entity type. For example,
`flowNodeInstanceId` is the unique Long key for a flow node instance, not a human-readable string.
When in doubt, assume the C7 convention applies.

When you see `processDefinitionKey` holding a string like `"invoice-process"`, that is **correct**
C7 usage. Do **not** rename it to `processDefinitionId` or add
`@JsonProperty("processDefinitionKey")` in an attempt to "fix" it.

For the complete field-by-field mapping between Optimize names and C8 Zeebe names, you can use the
Zeebe import service code as reference.
