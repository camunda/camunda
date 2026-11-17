# Optimize Module — Agent Instructions

Read the monorepo-wide agent instructions first:

@../AGENTS.md

---

## Optimize-Specific Notes

### C7/C8 Terminology Warning

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

### Exporter Filter ↔ Optimize Importer Coupling

The Zeebe Elasticsearch/OpenSearch **exporter filter** has an Optimize mode
(`OptimizeModeFilter` in `zeebe/exporter-filter`) that exports only the records and intents
Optimize needs. This list must stay in sync with the Optimize importer:

- **When the Optimize importer starts consuming a new record type or intent**, verify and update
  `OptimizeModeFilter` so those records are not silently dropped before reaching the indices.
- **Any change to the Optimize importer regarding records or intents** must be evaluated from the
  exporter filter perspective (and vice versa) to keep both sides consistent.

### Build

Optimize is skipped by `-Dquickly` at the monorepo level. To build only Optimize:

```bash
./mvnw install -pl optimize -am -T1C
```

Frontend (Yarn — see `.github/instructions/frontend.instructions.md` for details):

```bash
cd optimize/client
yarn install
yarn build
```

