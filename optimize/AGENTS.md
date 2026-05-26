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

This convention applies to Optimize fields that represent Zeebe-derived process, decision, and
instance identifiers, not to every `...Id` / `...Key` field in the codebase. In those cases,
`...Id` typically holds the unique Zeebe `Long` key (often stored as `String`), while `...Key`
holds the non-unique BPMN/DMN identifier string. For example, `flowNodeInstanceId` is the unique
Long key for a flow node instance, not a human-readable string. Generic identifiers such as
`tenantId`, `userId`, and similar fields are exceptions and must be interpreted according to their
own domain semantics. When in doubt, verify the specific field usage instead of assuming the C7
convention always applies.

When you see `processDefinitionKey` holding a string like `"invoice-process"`, that is **correct**
C7 usage. Do **not** rename it to `processDefinitionId` or add
`@JsonProperty("processDefinitionKey")` in an attempt to "fix" it.

For the complete field-by-field mapping between Optimize names and C8 Zeebe names, you can use the
Zeebe import service code as reference.

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

