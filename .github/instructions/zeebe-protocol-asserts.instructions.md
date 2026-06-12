```yaml
---
applyTo: "zeebe/protocol-asserts/**"
---
```
# Zeebe Protocol AssertJ Assertions (Generated Module)

## Purpose

This module auto-generates type-safe AssertJ assertion classes for all types in the `io.camunda.zeebe.protocol.record` package (from `zeebe-protocol` and `camunda-security-protocol`). It contains **zero hand-written source files** — all ~217 Java files are produced at build time by the `assertj-assertions-generator-maven-plugin` v2.2.0. The generated assertions provide fluent `has*()` methods for every getter on protocol record values, intents, and enums, enabling precise test assertions against Zeebe engine records.

## Architecture

### Generation Pipeline (3-phase Maven build)

1. **`generate-sources`**: `assertj-assertions-generator-maven-plugin` scans `io.camunda.zeebe.protocol.record` and all sub-packages, producing assertion classes into `target/generated-sources/assertj-assertions/`. Excludes `Immutable*` types (asserts exist for their interfaces). `hierarchical` is `false` to avoid requiring tedious downcasts (e.g., `JobRecordValue` gets `hasVariables()` directly, not only via `RecordValueWithVariables`).
2. **`process-sources`**: `maven-replacer-plugin` patches `RecordAssert.java` to replace `T value`/`T actualValue` with `RecordValue value`/`RecordValue actualValue` — workaround for [assertj-generator #92](https://github.com/joel-costigliola/assertj-assertions-generator/issues/92) (no generics support).
3. **`generate-sources`** (Eclipse): `build-helper-maven-plugin` re-declares the generated directory as a source root for IDE compatibility.

### Generated Package Structure

```
target/generated-sources/assertj-assertions/io/camunda/zeebe/protocol/record/
├── Assertions.java                    # Static factory entry point (assertThat overloads)
├── SoftAssertions.java                # Soft assertion entry point
├── RecordAssert.java                  # Patched: generics T → RecordValue
├── *Assert.java                       # Top-level record types, SBE codecs, enums
├── intent/                            # Intent enum asserts (~40 files)
│   ├── management/                    # Management intent asserts
│   └── scaling/                       # Scaling intent asserts
└── value/                             # RecordValue interface asserts (~100+ files)
    ├── deployment/                    # Deployment-related value asserts
    ├── management/                    # Management value asserts
    └── scaling/                       # Scaling value asserts
```

### Input Dependencies

- **`zeebe-protocol`** (`io.camunda.zeebe.protocol.record`): Core interfaces — `Record`, `RecordValue`, `RecordValueWithVariables`, `ValueTypeMapping`, all `*RecordValue` interfaces (e.g., `ProcessInstanceRecordValue`, `JobRecordValue`), intent enums, value enums, SBE codec types.
- **`camunda-security-protocol`** (`io.camunda.zeebe.protocol.record.value`): Auth-related types — `AuthorizationOwnerType`, `AuthorizationResourceType`, `PermissionType`, `AuthorizationScope`, `DefaultRole`, `EntityType`.

## How Consumers Use This Module

Add as a **test-scoped** dependency. Use `Assertions.assertThat(recordValue)` for type-specific fluent assertions:

```java
import io.camunda.zeebe.protocol.record.Assertions;
// then:
Assertions.assertThat(incident.getValue())
    .hasProcessInstanceKey(processInstanceKey)
    .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
    .hasErrorMessage("...");
```

Import specific assert classes directly when needed: `import io.camunda.zeebe.protocol.record.value.IncidentRecordValueAssert;`

### Consumers (test scope)

- `zeebe/engine` (~190 test files) — primary consumer for engine processor/behavior tests
- `zeebe/gateway-rest` — REST controller tests
- `zeebe/protocol-jackson` — serialization round-trip tests
- `zeebe/protocol-test-util` — shared test utilities
- `zeebe/qa/integration-tests` — integration tests
- `service/` — service layer tests

## Rules for Modifying This Module

1. **Never edit generated files** — they live in `target/` and are recreated every build. All changes must go through `pom.xml` configuration.
2. **To add assertions for new protocol types**: Add the new interface/enum to `zeebe-protocol` or `camunda-security-protocol` under `io.camunda.zeebe.protocol.record.*`. The generator picks it up automatically on next build.
3. **To exclude types from generation**: Add a regex `<exclude>` pattern in the plugin `<excludes>` section of `pom.xml` (line 55–60).
4. **To regenerate**: Run `./mvnw -pl zeebe/protocol-asserts -am generate-sources -T1C`.
5. **If `RecordAssert.java` compilation fails with generic type errors**: The `replacer` plugin likely needs updating — check that `T value` → `RecordValue value` and `T actualValue` → `RecordValue actualValue` replacements in `pom.xml` (lines 88–98) still match the generator output.
6. **Do not enable `hierarchical=true`** — this was intentionally disabled so that each assert class includes all inherited getter assertions without requiring casts (see pom.xml comment, line 66–69).
7. **Do not enable BDD or JUnit soft assertions** — only standard assertions and soft assertions are generated (`generateBddAssertions=false`, `generateJUnitSoftAssertions=false`).

## Common Pitfalls

- **Missing assertions after adding a new RecordValue**: Ensure the new type is in package `io.camunda.zeebe.protocol.record` (or sub-package) and does not match the `^.*\.Immutable.*$` exclusion pattern. Rebuild with `generate-sources`.
- **`javax.annotation` dependency warning**: The `javax.annotation-api` dependency is required by generated `@Generated` annotations but flagged by `maven-dependency-plugin`; it is explicitly listed in `<ignoredDependencies>` (line 139).
- **IDE not seeing generated classes**: Run `./mvnw -pl zeebe/protocol-asserts generate-sources` then refresh the project. The `build-helper-maven-plugin` adds the generated directory as a source root.

## Key Files

- `zeebe/protocol-asserts/pom.xml` — sole configuration file; controls generator, replacer, and build-helper plugins
- `zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/` — source interfaces that drive generation
- `security/security-protocol/src/main/java/io/camunda/zeebe/protocol/record/value/` — auth types that drive generation
- `target/generated-sources/assertj-assertions/.../Assertions.java` — generated static factory entry point
- `target/generated-sources/assertj-assertions/.../RecordAssert.java` — patched generic-safe Record assertion