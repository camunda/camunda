```yaml
---
applyTo: "zeebe/feel-tagged-parameters/**"
---
```
# Zeebe FEEL Tagged Parameters

## Module Purpose

This module provides static analysis of parsed FEEL (Friendly Enough Expression Language) expressions to extract "tagged" function invocations — specifically the `fromAi()` function — into structured metadata (`TaggedParameter` records). It enables the engine to introspect FEEL expressions at deployment time and extract parameter schemas for AI-driven ad-hoc subprocess activities, without evaluating the expressions.

## Architecture

The module has three layers:

1. **Data model**: `TaggedParameter` — a Java record holding extracted parameter metadata (name, description, type, schema, options).
2. **Extraction engine**: `TaggedParameterExtractor` — walks the FEEL AST (`ParsedExpression`) recursively via Scala `Product` iteration, delegating to function-specific extractors.
3. **Function-specific extractors**: Implementations of `FunctionInvocationTaggedParameterExtractor` — currently only `FromAiTaggedParameterExtractor` for the `fromAi()` function.

Data flow: `ParsedExpression` (FEEL AST) → `TaggedParameterExtractor.extractParameters()` → recursive AST walk → `FunctionInvocation` nodes matched by function name → delegated to `FromAiTaggedParameterExtractor.extract()` → `TaggedParameter` records returned.

## Key Abstractions

- **`TaggedParameter`** (`impl/TaggedParameter.java`): Immutable record with fields `name`, `description`, `type`, `schema` (Map), `options` (Map). All fields except `name` are nullable.
- **`TaggedParameterExtractor`** (`impl/TaggedParameterExtractor.java`): Entry point. Maintains a `Map<String, FunctionInvocationTaggedParameterExtractor>` keyed by function name. Default constructor registers `FromAiTaggedParameterExtractor`. Constructor accepts custom extractor list for extensibility.
- **`FunctionInvocationTaggedParameterExtractor`** (`impl/FunctionInvocationTaggedParameterExtractor.java`): Strategy interface with `functionName()` and `extract(FunctionInvocation)`.
- **`FromAiTaggedParameterExtractor`** (`impl/FromAiTaggedParameterExtractor.java`): Extracts `fromAi()` invocations supporting both positional and named parameters. Converts Scala FEEL AST nodes (`ConstString`, `ConstNumber`, `ConstBool`, `ConstList`, `ConstContext`, `Ref`) to Java types.
- **`FromAiFunction`** (`impl/FromAiFunction.java`): Defines the `fromAi` FEEL function as a `JavaFunction` with 5 overloaded arities (1–5 params). At runtime, it simply passes through the first argument. Registered in `zeebe/feel/.../FeelFunctionProvider.java`.

## Relationships

- **Consumed by `zeebe/engine`**: `AdHocSubProcessTransformer` uses `TaggedParameterExtractor` at deployment time to extract `fromAi()` parameters from ad-hoc activity input mappings and serialize them as `AdHocActivityParameter` metadata.
- **Consumed by `zeebe/feel`**: `FeelFunctionProvider` registers `FromAiFunction.INSTANCES` so the FEEL engine recognizes `fromAi()` as a valid function during expression evaluation.
- **Depends on `feel-engine`**: Uses `org.camunda.feel.syntaxtree.*` and `org.camunda.feel.context.JavaFunction` from the FEEL-Scala library.
- **Depends on `scala-library`**: Required for Scala interop (`Product`, `CollectionConverters`).

## Design Patterns

- **Strategy pattern**: `FunctionInvocationTaggedParameterExtractor` interface allows adding new tagged function extractors without modifying `TaggedParameterExtractor`. Register new extractors via the constructor's `List` parameter.
- **Recursive AST visitor**: `TaggedParameterExtractor.extractParameters()` walks the Scala `Product` tree using `productIterator()`, collecting all matching `FunctionInvocation` nodes at any depth.
- **Scala-Java interop**: Use `scala.jdk.javaapi.CollectionConverters.asJava()` exclusively for converting Scala collections. Never use deprecated `scala.collection.JavaConverters`.

## Extension Points

To add support for a new tagged function (e.g., `fromUser()`):
1. Create a new class implementing `FunctionInvocationTaggedParameterExtractor` in the `impl` package.
2. Implement `functionName()` returning the FEEL function name and `extract()` converting `FunctionInvocation` params to a `TaggedParameter`.
3. Register the new extractor in `TaggedParameterExtractor`'s default constructor list alongside `FromAiTaggedParameterExtractor`.
4. Define the corresponding `JavaFunction` (like `FromAiFunction`) and register it in `zeebe/feel/.../FeelFunctionProvider.java`.

## Invariants

- The `value` parameter of `fromAi()` must be a `Ref` (variable reference like `toolCall.customParam`), never a literal. The extractor throws `IllegalArgumentException` if this is violated.
- Parameters `description` and `type` must be `ConstString` literals (no expressions). Parameters `schema` and `options` must be `ConstContext` literals (no computed contexts).
- The extractor operates on the **parsed AST only** — it never evaluates expressions. All parameter values must be statically determinable constants.
- `FromAiFunction.invoke()` is a pass-through at runtime: it returns the first argument unchanged.

## Common Pitfalls

- Do not pass computed/dynamic values as `fromAi()` parameters (e.g., `string join(...)` for description). The extractor requires static AST constants and will throw `IllegalArgumentException`.
- When adding nested constant conversion in `FromAiTaggedParameterExtractor.convertConstant()`, handle all expected `Exp` subtypes — unhandled types throw `IllegalArgumentException`.
- Scala `Product.productIterator()` returns a Scala iterator; always convert with `CollectionConverters.asJava()` before iterating in Java.
- The SpotBugs exclude filter (`spotbugs/spotbugs-exclude.xml`) suppresses `NP_ALWAYS_NULL` for Scala-generated classes — do not remove these suppressions.

## Testing

- Tests use `FeelEngineBuilder.forJava().build()` to parse FEEL expressions, then pass `ParsedExpression` to `TaggedParameterExtractor`.
- Test cases cover: positional params, named params, mixed-order named params, nested contexts/lists, expressions embedded in arithmetic/concatenation/context/list structures, and error cases for type mismatches.
- Run scoped: `./mvnw -pl zeebe/feel-tagged-parameters -am test -DskipITs -DskipChecks -T1C`

## Reference Files

- `src/main/java/io/camunda/zeebe/feel/tagged/impl/TaggedParameterExtractor.java` — core recursive AST walker
- `src/main/java/io/camunda/zeebe/feel/tagged/impl/FromAiTaggedParameterExtractor.java` — fromAi() parameter extraction with Scala→Java type conversion
- `src/main/java/io/camunda/zeebe/feel/tagged/impl/FromAiFunction.java` — FEEL function definition with 5 arity overloads
- `src/main/java/io/camunda/zeebe/feel/tagged/impl/FunctionInvocationTaggedParameterExtractor.java` — strategy interface for extensibility
- `src/test/java/io/camunda/zeebe/feel/tagged/impl/TaggedParameterExtractorTest.java` — comprehensive parameterized test suite