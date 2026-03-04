```yaml
---
applyTo: "zeebe/expression-language/src/**"
---
```
# Zeebe Expression Language Module

## Purpose

This module provides the expression language abstraction for the Zeebe process engine. It wraps the FEEL-Scala engine (`org.camunda.feel:feel-engine`) behind clean Java interfaces, enabling BPMN/DMN expression parsing and evaluation with MessagePack-encoded variable contexts. The module defines the public API (`io.camunda.zeebe.el`) consumed by the engine and separates it from the FEEL-specific implementation (`io.camunda.zeebe.el.impl`).

## Architecture

### Two-Layer Design

- **Public API** (`io.camunda.zeebe.el`): Interfaces and factory — `ExpressionLanguage`, `Expression`, `EvaluationResult`, `EvaluationContext`, `ResultType`, `EvaluationWarning`, `ExpressionLanguageMetrics`. This is the contract consumed by the engine module.
- **Implementation** (`io.camunda.zeebe.el.impl`): FEEL-backed implementations — `FeelExpressionLanguage`, `FeelExpression`, `FeelEvaluationResult`, `FeelVariableContext`, `StaticExpression`, `InvalidExpression`, `NullExpression`, `EvaluationFailure`.

### Expression Classification

Parsing (`parseExpression`) classifies input into three categories based on the `=` prefix pattern (`Pattern.compile("\\=(.+)", Pattern.DOTALL)` in `FeelExpressionLanguage`):

1. **FEEL expressions** (prefix `=`): Stripped of `=`, parsed by FEEL engine → `FeelExpression` (valid) or `InvalidExpression` (parse failure).
2. **Static expressions** (no `=` prefix): Treated as literal values → `StaticExpression`. Auto-detects type: `null`, `true`/`false`, numeric (`BigDecimal`), or string.
3. **Null expressions**: Handled by `NullExpression` (used externally, not by the parser directly).

### Data Flow

1. Engine calls `ExpressionLanguageFactory.createExpressionLanguage(clock)` or `createExpressionLanguage(clock, metrics)`.
2. Parser returns an `Expression` object (reusable, can be stored in process model cache).
3. Engine calls `evaluateExpression(expression, context)` with an `EvaluationContext`.
4. `EvaluationContext.getVariable(name)` returns `Either<DirectBuffer, EvaluationContext>` — `Left` for terminal MessagePack values, `Right` for nested object contexts that support further path resolution.
5. `FeelVariableContext` adapts `EvaluationContext` to FEEL's `CustomContext`/`VariableProvider` via recursive wrapping.
6. Result is a `FeelEvaluationResult` wrapping FEEL's `Val` type, with lazy MessagePack serialization via `FeelToMessagePackTransformer`.

### Variable Context Bridge

`FeelVariableContext` (line 18–49) bridges between Zeebe's `EvaluationContext` and FEEL's `VariableProvider`. It maps:
- `Left(buffer)` with non-null/non-empty buffer → `Option.apply(directBuffer)` (raw MessagePack passed to FEEL)
- `Left(null)` or empty buffer → `Option.empty()` (variable absent)
- `Right(nestedContext)` → `Option.apply(new ValContext(new FeelVariableContext(nestedContext)))` (recursive wrapping for path expressions like `x.y.z`)

## Key Abstractions

| Type | Role |
|------|------|
| `ExpressionLanguage` | Core interface: `parseExpression(String)` and `evaluateExpression(Expression, EvaluationContext)` |
| `ExpressionLanguageFactory` | Static factory; only entry point for creating instances. Never instantiate `FeelExpressionLanguage` directly from outside this module. |
| `Expression` | Parsed expression with `isValid()`, `isStatic()`, `getVariableName()`. Implementations: `FeelExpression`, `StaticExpression`, `InvalidExpression`, `NullExpression` |
| `EvaluationContext` | `@FunctionalInterface` for variable resolution. Returns `Either<DirectBuffer, EvaluationContext>` per segment. |
| `EvaluationResult` | Evaluation output with typed accessors (`getString()`, `getBoolean()`, `getNumber()`, `getDuration()`, `getPeriod()`, `getDateTime()`, `getList()`). Always check `isFailure()` before accessing values. |
| `ResultType` | Enum: `UNKNOWN`, `NULL`, `BOOLEAN`, `NUMBER`, `STRING`, `DURATION`, `PERIOD`, `DATE`, `DATE_TIME`, `ARRAY`, `OBJECT` |
| `ExpressionLanguageMetrics` | Interface for parsing/evaluation duration recording. Has `noop()` factory for tests. |

## Dependencies and Consumers

- **Depends on**: `zeebe-feel-integration` (provides `FeelFunctionProvider`, `FeelToMessagePackTransformer`, `MessagePackValueMapper`), `feel-engine` (FEEL-Scala), `zeebe-util` (`Either`, `EnsureUtil`), `zeebe-msgpack-core`, `agrona` (`DirectBuffer`), `micrometer-core`
- **Primary consumer**: `zeebe/engine` — via `ExpressionProcessor`, `ExpressionBehavior`, `BpmnFactory`, `VariableMappingTransformer`, and ~60 other engine classes that parse/evaluate BPMN expressions.

## Metrics

Two Micrometer timers documented in `ExpressionLanguageMetricsDoc`:
- `zeebe.feel.expression.parsing.duration` — tagged with `outcome=success|failure`
- `zeebe.feel.expression.evaluation.duration` — tagged with `outcome=success|failure`

Slow evaluation detection threshold: 200ms (configurable via `ExpressionLanguageMetricsImpl` constructor). Slow evaluations are logged at WARN level via `Loggers.LOGGER`.

## Patterns and Invariants

- **No-throw contract**: Parsing and evaluation never throw on invalid input — they return `InvalidExpression` or `EvaluationFailure` with `isFailure() == true` and a `failureMessage`. Only programming errors (null args, unexpected types) throw.
- **StaticExpression dual role**: `StaticExpression` implements both `Expression` and `EvaluationResult` — evaluating a static expression returns the expression itself.
- **NullExpression dual role**: Same pattern as `StaticExpression`; implements both interfaces, returns `ResultType.NULL` and MessagePack NIL.
- **Buffer lifetime**: `DirectBuffer` from `EvaluationContext.getVariable()` is valid only until the next call into the same context. Clone if needed.
- **MessagePack encoding**: All results serialize to MessagePack via `toBuffer()`. `StaticExpression.toBuffer()` handles its own serialization; `FeelEvaluationResult.toBuffer()` delegates to `FeelToMessagePackTransformer`.
- **Clock injection**: `FeelEngineClock` controls `now()` and `today()` FEEL functions. Use `TestFeelEngineClock` in tests.

## Extension Points

- **Custom FEEL functions**: Add to `FeelFunctionProvider` in `zeebe/feel` module (not this module).
- **New result types**: Add to `ResultType` enum, add accessor to `EvaluationResult`, implement in `FeelEvaluationResult.getType()` switch and typed accessor.
- **Custom `EvaluationContext`**: Implement the functional interface. Engine provides `ScopedEvaluationContext`, `CombinedEvaluationContext`, `NamespacedEvaluationContext`, etc.

## Testing

- Use `TestFeelEngineClock` from `src/test/java/.../util/` for clock control.
- Provide `EvaluationContext` as lambda: `name -> Either.left(asMsgPack(...))` for simple cases, `name -> Either.left(null)` for empty contexts.
- Run scoped: `./mvnw -pl zeebe/expression-language -am test -DskipITs -DskipChecks -T1C`
- Test classes follow `should*` naming with `// given`, `// when`, `// then` sections.

## Common Pitfalls

- Never pass raw strings to `evaluateExpression` — always parse first via `parseExpression`.
- Always check `isFailure()` on `EvaluationResult` before calling typed accessors — they return `null` on failure.
- The `=` prefix is **required** for FEEL evaluation; without it, the string is treated as a static literal. This is by design for BPMN compatibility.
- `getList()` returns `List<DirectBuffer>` (MessagePack-encoded items), not deserialized values. Use `getListOfStrings()` only when all items are strings.
- `getDateTime()` for `ValLocalDateTime` uses `ZoneId.systemDefault()` — results are JVM-timezone-dependent.

## Key Files

- `main/.../ExpressionLanguage.java` — public API contract (2 methods)
- `main/.../impl/FeelExpressionLanguage.java` — core implementation: parsing, evaluation, metrics, slow-eval logging
- `main/.../EvaluationContext.java` — `@FunctionalInterface` for variable resolution with `Either`-based path traversal
- `main/.../impl/FeelEvaluationResult.java` — FEEL `Val` → typed result mapping with MessagePack serialization
- `main/.../impl/StaticExpression.java` — dual `Expression`+`EvaluationResult` for literal values
- `test/.../ExpressionLanguageTest.java` — canonical tests for parse/evaluate lifecycle
- `test/.../FeelExpressionTest.java` — FEEL-specific expression evaluation tests