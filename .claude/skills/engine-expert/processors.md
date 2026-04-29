# Processors

Processors are where the engine's logic lives. They consume commands and produce events, rejections, follow-up commands, and side-effects. The stream processor is **single-threaded** — only one command is processed at a time per partition.

## Iron rules

- **Bulk of logic lives here**, not in event appliers. Processors are easier to fix when bugs are found or behavior must be adjusted.
- **Read-only state access only.** Mutation goes through events + appliers. Use the `*State` (read-only) interface from a processor, never the `Mutable*State` interface.
- **Composition over inheritance.** Reuse logic via `Behavior` classes (e.g., `BpmnJobActivationBehavior`), not via subclasses.
- **A processor must always end the command's processing by appending an event or a rejection.** A command is considered processed (after replay) when a follow-up record points to it.
- **Exceptions are a last-resort rollback mechanism**, not a control-flow tool. They are expensive at engine throughput.
  - Prefer pre-validation: validate the command before appending any events.
  - Throw only when validation is only possible mid-processing, after some events have already been appended. The stream processor will roll back the appended records and call `tryHandleError`, where the processor typically appends a command rejection to recover.
- **Don't trust data in a command's `RecordValue`** — it may be stale by the time the command is processed. Read the latest entity data from state. Use the command's data only as the *requested change* (e.g., new assignee), and use state as the *basis* for the change.
- **Generated keys must be used as the record key of at least one appended record.** The key generator is rehydrated on replay by reading the keys of appended records. If a generated key is only stored inside a `RecordValue` (not used as a record key), the generator won't see it on replay and may hand out the same key again. Storing the key inside the record value as well is fine, but doesn't substitute for using it as a record key.
- **Authorization checks belong on every user-triggered command**, before mutating state. Internal/engine-triggered events (e.g., `ProcessInstance.COMPLETE_ELEMENT` fired by the engine itself) do **not** require an authorization check — those state-machine transitions assume permissions were verified upstream.
- **Inter-partition command receivers must be idempotent.** Prefer reject-redundant-command (write a rejection, no state change) over re-emitting the same events.
- **Take small steps.** Append follow-up commands rather than doing everything in one processing cycle. The single thread is shared with every other command on this partition.

## Error and rejection messages

- **Follow the format** `Expected [X], but got [Y] [in CONTEXT]`. Include execution context (partition ID, entity key, etc.) when it helps the user act on the failure.
- **Rejection reasons use the same pattern** — they surface to the user, so write them as remediation guidance, not as debug output.
- Full guidance: https://github.com/camunda/camunda/wiki/Error-Guidelines

## Logging

- **Hot paths log at trace level only.** The engine processes thousands of commands per second; INFO/DEBUG on a hot path is a measurable performance regression.
- **Use SLF4J's parameterized formatting** (`log.info("X {}", arg)`), never `String.format`. SLF4J skips formatting when the level is disabled; `String.format` allocates regardless.
- Levels: `TRACE` = component execution detail (granular loggers only); `DEBUG` = developer diagnostics; `INFO` = operator-facing events (leader changes, membership updates); `WARN` = expected, self-resolving errors that need monitoring (timeouts, transient unavailability); `ERROR` = critical failures requiring immediate attention.
- Full guidance: https://github.com/camunda/camunda/wiki/Logging

## Templates

- Processor: `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/user/UserCreateProcessor.java`
- Behavior class: `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/bpmn/behavior/BpmnJobActivationBehavior.java`
- Registration: `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/EngineProcessors.java`

## Canonical docs

- `zeebe/engine/README.md` § "Do's and Don'ts" — full list of stream-processor invariants.
- `docs/zeebe/developer_handbook.md` § "Authorization Checks in the Engine" — when and how to add authorization to a processor.
- `docs/zeebe/engine_questions.md` — variable scoping, joining gateways, token semantics.

