# Records, Value Types, and Intents

Records are the engine's data carriers. They flow through the log stream during processing and are exported to secondary storage (Elasticsearch / OpenSearch / RDBMS) for consumers (REST API, Operate, Tasklist, Optimize). The schema must therefore serve **both** the engine's processing needs **and** the consumers' read needs.

## Iron rules

- **Naming alignment is mandatory.** `ValueType.USER` ↔ `UserIntent` ↔ `UserRecord` ↔ `UserRecordValue`. Misalignment has slipped through before — block it on review.
- **Schema serves both processing and exporting.** Exporters typically cannot read state before writing (rare exceptions only). So any aggregation or derived value the consumers need must be **computed in the processor and written into the event**. Schema must support both:
  - the **input data** for state mutation (e.g., the increment requested by this command), and
  - the **output data** consumers need (e.g., the resulting cumulative total).
  - Concrete example: a counter record carries `incrementBy` (input from the command) **and** `totalAfter` (output computed by the processor and persisted in the event).
- **SBE messages are extended, not changed in incompatible ways.** Adding fields is fine; reordering, retyping, or removing existing fields is not.
- **Sensitive fields use `#sanitized()`** (e.g., `new IntegerProperty("token").sanitized()`) so they are excluded from logs and `toString()` while still being serialized to JSON for export.

## Workflow

Read `docs/zeebe/developer_handbook.md` before starting. The canonical step-by-step lives there:

- For a new record/value type: § "How to create a new record" walks through the seven phases (protocol, protocol-impl, exporters, test setups, official docs, Camunda Process Test, supported value types).
- For extending an existing record: § "How to extend an existing record" — the smaller four-step variant.

The skill's role is to surface the iron rules above, not to replace the handbook.

## Templates

- `RecordValue` interface: `zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/value/UserRecordValue.java`
- `RecordValue` implementation: `zeebe/protocol-impl/src/main/java/io/camunda/zeebe/protocol/impl/record/value/user/UserRecord.java`
- Intent: `zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/intent/UserIntent.java`
- Mapping: `zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/ValueTypeMapping.java`
- Serialization tests: `zeebe/protocol-impl/src/test/java/io/camunda/zeebe/protocol/impl/JsonSerializableToJsonTest.java` (extend with both a fully-populated case and a minimal/empty case)

## Canonical docs

- `docs/zeebe/developer_handbook.md` — new and extended record walkthroughs.
- `zeebe/engine/README.md` § "Record values are data objects that are often reused across different records of the same value type" — when to introduce a sub-record-value type.

