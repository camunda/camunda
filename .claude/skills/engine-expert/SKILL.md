---

name: engine-expert
description: Use when implementing new capabilities or fixing bugs in the Zeebe workflow engine in zeebe/engine/ — BPMN process execution, DMN decision evaluation, job lifecycle, user/identity management, batch operations, process variables, deployments, signals, messages, timers, multi-tenancy, or authorization. Use when adding or modifying processors, event appliers, state classes, record value types, intents, or engine tests.

---

# Engine Expert

Reference for making changes to the Zeebe workflow engine with confidence. The engine processes thousands of commands per second on a single thread, state is rebuilt from the log on every restart, and partitions communicate over an unreliable network. Mistakes here cause leader/follower divergence, upgrade-path breakage, or production performance regressions. This skill exists to prevent the well-known classes of mistake.

## Iron rules

These are summaries. The reference files (linked below) are authoritative — they carry the rationale, exceptions, and worked examples.

- No state mutation from processors. State changes only through events applied by event appliers.
- Released event appliers — and any method on a `Mutable*State` interface or anything transitively called from an applier — must not change in logic. Add a new version/method instead. Cosmetic changes (formatting, imports, comments, behavior-equivalent renames) are fine. No golden file protects state class methods — extra care required.
- Processors must end the command's processing by appending an event or a rejection. Exceptions are a last-resort rollback mechanism and are expensive — prefer pre-validation.
- Generated keys must be used as the record key of at least one appended record. Otherwise the key generator can't be rehydrated on replay and may hand out a duplicate.
- Inter-partition command receivers must be idempotent. Prefer reject-redundant-command over re-emitting events.
- Hot paths log at trace level only. INFO/DEBUG on a hot path is a performance regression at engine throughput.
- Values returned from `ColumnFamily.get(...)` / state reads are backed by a shared, mutable buffer reused on the next read of the same column family. Never cache them or hold them across another read — `copyFrom(...)` if you need to keep the value, or use `get(key, valueSupplier)` to allocate a fresh instance.

## Where to read next

|                                                 If you are…                                                  |        Read         |
|--------------------------------------------------------------------------------------------------------------|---------------------|
| Designing or extending a record value type, intent, or `ValueType`                                           | `records.md`        |
| Editing or creating a processor, behavior class, validation, or rejection                                    | `processors.md`     |
| Editing or creating an event applier, a `Mutable*State` interface method, or anything called from an applier | `event-appliers.md` |
| Writing or modifying engine tests                                                                            | `testing.md`        |

## Local checks before commit

Run only the tests relevant to your change — running the full engine test suite locally takes a long time and is what CI is for.

```bash
# 1. Format (mandatory before commit when touching Java/markdown/pom.xml).
./mvnw license:format spotless:apply -T1C

# 2. Tests scoped to your change (single class or small pattern). Use
#    -Dtest=YourTest, comma-separated classes, or a glob like '*UserTest*'.
./mvnw verify -pl zeebe/engine \
  -Dtest='YourTest' -DskipTests=false -DskipITs -Dquickly

# 3. If you touched an event applier, a Mutable*State method, or anything
#    transitively called from an applier — also run the golden file check.
./mvnw verify -pl zeebe/engine -Dtest=NoChangesTest \
  -DskipTests=false -DskipITs -Dquickly
```

Re-run any new or modified test at least 3× to catch flakiness. Push and let CI run the comprehensive suite.

## Recommended workflow for new processor logic

1. Write the test first, using `EngineRule` and `RecordingExporter` against appended records (see `testing.md`). Run it — confirm it fails.
2. Implement the processor (and its event applier + state changes).
3. Re-run the test 3+ times. Then run the local checks block above.

## Canonical docs

- `zeebe/engine/README.md` — architecture, do's and don'ts.
- `docs/zeebe/event-applier-golden-files.md` — golden files, versioning, port rules.
- `docs/zeebe/developer_handbook.md` — step-by-step for new records, REST endpoints, authorization.
- `docs/zeebe/engine_questions.md` — FAQ on tokens, joining gateways, variable scoping.
- https://github.com/camunda/camunda/wiki/Logging — logging guidance.
- https://github.com/camunda/camunda/wiki/Error-Guidelines — error message format and rejection vs. exception.

