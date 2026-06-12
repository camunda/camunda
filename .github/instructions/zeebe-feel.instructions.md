```yaml
---
applyTo: "zeebe/feel/**"
---
```
# Zeebe FEEL Integration Module

## Purpose

This module (`zeebe-feel-integration`) bridges the FEEL-Scala expression language engine (`org.camunda.feel:feel-engine`) with Zeebe's internal MessagePack-based data representation. It provides bidirectional conversion between FEEL `Val` types and MessagePack binary format, a lazy variable context that reads MessagePack maps on demand, and custom FEEL functions (`cycle`, `fromAi`). It is a pure library with no Spring dependencies — consumed by `zeebe/expression-language` and `zeebe/dmn`.

## Architecture

The module contains exactly 5 source files in a single package `io.camunda.zeebe.feel.impl`:

- **`MessagePackValueMapper`** — Extends `JavaValueMapper` from FEEL-Scala. Converts `DirectBuffer` (MessagePack) → FEEL `Val` on input (`toVal`), and preserves `Val` as-is on output (`unpackVal`). This is the entry point for FEEL to read process variables.
- **`FeelToMessagePackTransformer`** — Converts FEEL `Val` results back into `DirectBuffer` (MessagePack) for storage. Handles all `Val` subtypes: null, number (integer vs float), boolean, string, list, context, date/time types, durations. Unsupported types produce `nil` with a trace log.
- **`MessagePackContext`** — Implements FEEL-Scala's `CustomContext` for lazy map access. Reads a MessagePack map's key-value spans once at construction, then provides `DirectBuffer` views on demand via `VariableProvider`. Inner record `Span(offset, length)` tracks byte positions.
- **`FeelFunctionProvider`** — Extends `JavaFunctionProvider` to register custom functions: `cycle` (two overloads for BPMN timer cycle expressions) and `fromAi` (AI-tagged parameter marker, delegated to `zeebe-feel-tagged-parameters`).
- **`Loggers`** — Single shared SLF4J logger (`io.camunda.zeebe.feel`).

## Data Flow

```
Process Variables (MessagePack DirectBuffer)
    │
    ▼  MessagePackValueMapper.toVal()
FEEL Val (ValNumber, ValString, ValContext, ...)
    │
    ▼  FEEL-Scala engine evaluates expression
FEEL Val (result)
    │
    ▼  FeelToMessagePackTransformer.toMessagePack()
MessagePack DirectBuffer (stored back in engine state)
```

## Key Dependencies

| Dependency | Role |
|---|---|
| `org.camunda.feel:feel-engine` | FEEL expression language (Scala-based) — provides `Val` types, `JavaValueMapper`, `JavaFunctionProvider`, `CustomContext` |
| `zeebe-msgpack-core` | `MsgPackReader`/`MsgPackWriter` for binary serialization |
| `org.agrona:agrona` | `DirectBuffer`/`UnsafeBuffer`/`ExpandableArrayBuffer` for zero-copy I/O |
| `zeebe-feel-tagged-parameters` | `FromAiFunction` and `TaggedParameterExtractor` for AI-tagged expression parameters |
| `scala-library` | Required by FEEL-Scala interop (Scala collections, `Option`, `Tuple2`) |

## Consumers

- **`zeebe/expression-language`** (`FeelExpressionLanguage`) — instantiates `MessagePackValueMapper`, `FeelToMessagePackTransformer`, and `FeelFunctionProvider` to build the `FeelEngine`. This is the primary consumer.
- **`zeebe/dmn`** (`DmnScalaDecisionEngine`) — uses `FeelToMessagePackTransformer` directly to convert DMN decision outputs to MessagePack.
- **`zeebe/engine`** (`AdHocSubProcessTransformer`) — imports `TaggedParameterExtractor` from the sibling `feel-tagged-parameters` module (not from this module directly).

## Patterns and Invariants

- **Scala interop**: Use `scala.jdk.javaapi.CollectionConverters` for Scala↔Java collection conversion. Use `scala.Option`, `scala.Tuple2` when interacting with FEEL-Scala APIs. Never use deprecated `JavaConverters`.
- **MessagePack context is lazy**: `MessagePackContext` pre-reads key spans but does NOT deserialize values until `getVariable()` is called. This avoids unnecessary deserialization of unused variables.
- **Number handling**: `FeelToMessagePackTransformer` writes whole numbers as `writeInteger` and fractional numbers as `writeFloat`. `MessagePackValueMapper` reads `INTEGER` and `FLOAT` MsgPack tokens back into `ValNumber` with `scala.math.BigDecimal`.
- **Unsupported types fallback**: Both `MessagePackValueMapper.toVal()` and `FeelToMessagePackTransformer.writeValue()` use `nil`/`ValNull$` as fallback for unknown types, logging at WARN or TRACE level respectively.
- **No tests in this module**: This module has no `src/test` directory. Testing is done through `zeebe/expression-language` tests. Run: `./mvnw -pl zeebe/expression-language -am test -DskipITs -DskipChecks -T1C`.
- **All classes are in `impl` package**: The entire module is implementation detail. Public API is in `zeebe/expression-language` (`io.camunda.zeebe.el`).

## Extension Points

- **Adding a custom FEEL function**: Add the function class, then register it in `FeelFunctionProvider.FUNCTIONS` map. For complex tagged functions, add the implementation to `zeebe/feel-tagged-parameters` and reference it from `FeelFunctionProvider`.
- **Supporting new Val types**: Add a new `case` branch in both `FeelToMessagePackTransformer.writeValue()` and `MessagePackValueMapper.read()`.

## Common Pitfalls

- Never return raw Java objects from `MessagePackValueMapper.unpackVal()` — always return the `Val` to preserve FEEL type information.
- `FeelToMessagePackTransformer` reuses internal buffers (`writeBuffer`, `resultView`, `stringWrapper`) — it is NOT thread-safe. Each consumer must have its own instance.
- `MessagePackContext.MessagePackMapVariableProvider.getVariable()` returns a reusable `resultView` buffer — callers must clone the buffer if they need to retain the value across multiple `getVariable` calls.
- When writing Scala interop, use `MODULE$` to reference Scala singleton objects (e.g., `ValNull$.MODULE$`).
- The SpotBugs exclude file (`spotbugs/spotbugs-exclude.xml`) suppresses `NP_ALWAYS_NULL` for Scala-generated code — do not remove this exclusion.

## Key Files

| File | Role |
|---|---|
| `src/main/java/io/camunda/zeebe/feel/impl/MessagePackValueMapper.java` | MsgPack→Val conversion (input to FEEL engine) |
| `src/main/java/io/camunda/zeebe/feel/impl/FeelToMessagePackTransformer.java` | Val→MsgPack conversion (output from FEEL engine) |
| `src/main/java/io/camunda/zeebe/feel/impl/MessagePackContext.java` | Lazy MsgPack map variable provider |
| `src/main/java/io/camunda/zeebe/feel/impl/FeelFunctionProvider.java` | Custom FEEL functions (`cycle`, `fromAi`) |
| `pom.xml` | Module dependencies — `feel-engine`, `zeebe-msgpack-core`, `zeebe-feel-tagged-parameters` |