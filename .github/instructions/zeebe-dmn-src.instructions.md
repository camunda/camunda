```yaml
---
applyTo: "zeebe/dmn/src/**"
---
```
# Zeebe DMN Engine Module

## Purpose

This module provides the DMN (Decision Model and Notation) evaluation capability for the Zeebe process engine. It wraps the third-party DMN-Scala engine (`org.camunda.bpm.extension.dmn.scala:dmn-engine`) behind a clean Java interface layer, converting DMN evaluation inputs/outputs to and from MessagePack-encoded `DirectBuffer` values compatible with Zeebe's internal data representation.

## Architecture

The module follows a strict **API / implementation** separation:

- **Public API** (`io.camunda.zeebe.dmn`): 9 interfaces and 1 enum defining the contract for parsing and evaluating DMN resources. No implementation details leak through these types.
- **Implementation** (`io.camunda.zeebe.dmn.impl`): 10 classes wrapping DMN-Scala types. All are package-private to consumers via the factory pattern.
- **Factory entry point**: `DecisionEngineFactory.createDecisionEngine()` — the only way to obtain a `DecisionEngine` instance.

### Data Flow

```
InputStream (DMN XML)
  → DecisionEngine.parse()
    → DMN-Scala parser (Scala Either)
      → ParsedDmnScalaDrg (success) or ParseFailureMessage (failure)
        → ParsedDecisionRequirementsGraph (public API)

ParsedDecisionRequirementsGraph + decisionId + DecisionContext
  → DecisionEngine.evaluateDecisionById()
    → DMN-Scala eval (Scala Either)
      → AuditLog → EvaluatedDmnScalaDecision → EvaluatedDmnScalaInput/Output/Rule
      → Val output → FeelToMessagePackTransformer → DirectBuffer (MessagePack)
        → EvaluationResult (success) or EvaluationFailure (failure)
          → DecisionEvaluationResult (public API)
```

## Key Abstractions

| Interface/Class | Role |
|---|---|
| `DecisionEngine` | Core API: `parse(InputStream)` and `evaluateDecisionById(drg, id, context)` |
| `DecisionEngineFactory` | Static factory; sole way to instantiate the engine |
| `ParsedDecisionRequirementsGraph` | Parsed DMN resource; contains decisions, validity flag, failure message |
| `ParsedDecision` | A single decision within a DRG (id + name) |
| `DecisionContext` | Variable context for evaluation; exposes `toMap()` |
| `DecisionEvaluationResult` | Evaluation outcome: output buffer, failure info, evaluated decisions list |
| `EvaluatedDecision` | Per-decision audit: type, output, inputs, matched rules |
| `DecisionType` | Enum: `DECISION_TABLE`, `LITERAL_EXPRESSION`, `CONTEXT`, `INVOCATION`, `LIST`, `RELATION`, `UNKNOWN` |
| `DmnScalaDecisionEngine` | Sole implementation wrapping `org.camunda.dmn.DmnEngine` |
| `VariablesContext` | Simple `Map<String, Object>`-backed `DecisionContext` implementation |

## Consumer: Zeebe Engine

The primary consumer is `zeebe/engine` via two integration points:

- **`DmnResourceTransformer`** (`zeebe/engine/.../deployment/transform/`): calls `DecisionEngine.parse()` during deployment to validate DMN resources and extract decision metadata. Casts to `ParsedDmnScalaDrg` to access the raw `ParsedDmn` for storage.
- **`DecisionBehavior`** (`zeebe/engine/.../processing/common/`): calls `evaluateDecisionById()` at runtime during BPMN business rule task execution. Uses `VariablesContext` to pass process variables. Reads all `EvaluatedDecision`, `EvaluatedInput`, `EvaluatedOutput`, and `MatchedRule` data from the result to populate engine records.

## Design Patterns

- **No-throw result objects**: Both `parse()` and `evaluateDecisionById()` return result objects with `isValid()`/`isFailure()` checks instead of throwing exceptions. Parse failures return `ParseFailureMessage`; evaluation failures return `EvaluationFailure`. Only `IllegalArgumentException` is thrown for null input streams.
- **Scala interop via `foreach`**: DMN-Scala returns Scala collections (`scala.collection.Iterable`). The impl classes iterate using `.foreach()` with Java lambdas that return `Boolean` (the `ArrayList.add()` return), bridging Scala→Java collection conversion (see `ParsedDmnScalaDrg.getParsedDecisions()`, `DmnScalaDecisionEngine.getEvaluatedDecisions()`).
- **MessagePack encoding**: All decision values (`Val` from FEEL-Scala) are converted to MessagePack-encoded `DirectBuffer` via `FeelToMessagePackTransformer`. Buffers are cloned with `BufferUtil.cloneBuffer()` to avoid reuse issues.
- **Record types for audit data**: `EvaluatedDmnScalaDecision`, `EvaluatedDmnScalaInput`, `EvaluatedDmnScalaOutput`, `MatchedDmnScalaRule` are Java records with static `of()` factory methods that accept DMN-Scala audit types and a `Function<Val, DirectBuffer>` converter.

## Extension Points

- **New decision types**: Add a mapping entry in `EvaluatedDmnScalaDecision.DECISION_TYPE_MAPPING` and a corresponding value to `DecisionType` enum.
- **Alternative DMN engine**: Implement `DecisionEngine` interface and update `DecisionEngineFactory` to return the new implementation. No other code changes needed.
- **Custom variable context**: Implement `DecisionContext` with lazy variable resolution (see TODO comment at `DmnScalaDecisionEngine:98` referencing issue #8092).

## Invariants

- Never expose DMN-Scala types (`ParsedDmn`, `AuditLog`, `Val`) through the public API interfaces. Only `DmnResourceTransformer` in the engine casts to `ParsedDmnScalaDrg` to access `getParsedDmn()`.
- Always clone `DirectBuffer` returned by `FeelToMessagePackTransformer` — the transformer reuses an internal buffer.
- `EvaluatedInput.inputName()` favors the label over the expression; expression text is truncated at 30 characters.
- `EvaluatedOutput.outputName()` favors the label over the name (matching Camunda Modeler behavior).
- Matched rule indices are 1-based (not 0-based).
- A null `DecisionContext` is treated as an empty map (`Map::of`).

## Testing

- 3 test classes: `DmnParsingTest`, `DmnEvaluationTest`, `DmnEvaluatedDecisionsTest`
- Tests use `.dmn` resource files in `src/test/resources/` (7 DMN files covering decision tables, DRGs, assertions, decision types, I/O naming)
- Use `MsgPackUtil.asMsgPack()` and `MsgPackUtil.assertEquality()` from `zeebe-test-util` to verify MessagePack outputs
- Use `@Nested` + `@TestInstance(PER_CLASS)` + `@ParameterizedTest` for decision type and output variations
- Run scoped: `./mvnw -pl zeebe/dmn -am test -DskipITs -DskipChecks -T1C`

## Common Pitfalls

- Do not add new public types to `io.camunda.zeebe.dmn.impl` — the impl package is internal. Expose only via interfaces in `io.camunda.zeebe.dmn`.
- The `foreach` lambda on Scala collections must return a value (the `return` keyword before `ArrayList.add()` is the Scala `Function1` return, not a Java method return).
- Do not modify `DecisionType` enum ordinals — values are serialized in engine records and protocol.
- `ParsedDmnScalaDrg.of()` reads DRG metadata from `DmnModelInstance.getDefinitions()`, not from the DMN-Scala parsed representation. Keep both in sync if the model API changes.

## Key Files

- `main/.../DecisionEngine.java` — public API interface
- `main/.../impl/DmnScalaDecisionEngine.java` — core implementation wrapping DMN-Scala
- `main/.../impl/EvaluatedDmnScalaDecision.java` — audit record with decision type mapping
- `main/.../impl/ParsedDmnScalaDrg.java` — parsed DRG wrapping DMN-Scala's `ParsedDmn`
- `test/.../DmnEvaluatedDecisionsTest.java` — most comprehensive test covering DRG evaluation, decision types, and I/O naming