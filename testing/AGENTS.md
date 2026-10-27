# Testing Libraries — Agent Instructions

You are working in `testing/`, the **Camunda Process Test** (CPT) libraries.
Owner: `@camunda/c8-testing`. Read the monorepo-wide instructions first:

@../AGENTS.md

---

## Primary reference

[`CONTRIBUTING.md`](CONTRIBUTING.md) is the canonical guide for this area — **read it before
contributing**. It covers the module layout and dependency graph, the architecture, testing
conventions, CI, release management, and step-by-step how-tos. Do not duplicate its content here;
keep it the single source of truth and update it when conventions change.

## Module map

|                 Module                 |                                            Purpose                                             |
|----------------------------------------|------------------------------------------------------------------------------------------------|
| `camunda-process-test-java`            | Core: JUnit 5 extension, runtime, assertions, utilities, AI assertions, JSON test-case runner. |
| `camunda-process-test-json-test-cases` | JSON test-case model (schema + Immutables/Jackson POJOs). No runtime logic.                    |
| `camunda-process-test-coverage`        | Coverage report — Java backend + webpack frontend (has its own README/CONTRIBUTING).           |
| `camunda-process-test-langchain4j`     | LangChain4j bridge for LLM-as-a-judge and semantic-similarity assertions (SPI).                |
| `camunda-process-test-spring`          | Canonical Spring integration (Spring Boot 4 / Spring 6).                                       |
| `camunda-process-test-spring-boot-3`   | `-spring` repackaged for Spring Boot 3.5.x (no own sources).                                   |
| `camunda-process-test-spring-boot-4`   | Relocation stub → `-spring` (do not add code).                                                 |
| `camunda-process-test-example`         | Example project for demos and doc snippets.                                                    |

## Rules of thumb

- Keep the `io.camunda.process.test.api` (public) vs `.impl` split — never expose `impl` types.
- Add Spring features to `camunda-process-test-spring`; the `spring-boot-3`/`spring-boot-4` modules
  are packaging/compatibility only.
- Keep `json-test-cases` free of runtime logic. The JSON schema and Java interfaces are kept in sync
  **manually** — extend `PojoCompatibilityTest` when changing either.
- Prefer unit tests; name integration tests `*IT`; use `@CamundaAssertExpectFailure` when asserting
  a failure message (avoids the timeout wait).

## Build commands

```bash
# Build the module and its dependencies
./mvnw install -pl testing/camunda-process-test-java -am -Dquickly -T1C

# Unit tests only / a single class
./mvnw verify -pl testing/camunda-process-test-java -DskipTests=false -DskipITs -Dquickly
./mvnw verify -pl testing/camunda-process-test-java -Dtest=<TestClassName> -DskipTests=false -DskipITs -Dquickly

# Integration tests (require Docker / Testcontainers)
./mvnw verify -pl testing/camunda-process-test-java -Dit.test=<ITClassName> -DskipTests=false -DskipUTs -Dquickly

# Before committing (mandatory — formatting also covers markdown)
./mvnw license:format spotless:apply -T1C && \
./mvnw verify -pl testing/camunda-process-test-java -DskipTests=false -DskipITs -Dquickly
```

## Common how-tos

See the step-by-step guides in [`CONTRIBUTING.md`](CONTRIBUTING.md#8-common-contributions-how-to):

- **Add a new assertion** — `CamundaAssert` → `*Assert` interface → `*Assertj` impl → unit tests.
- **Add a new JSON test-case instruction** — schema → Immutables interface → type + handler →
  register in `CamundaTestCaseRunner` → `PojoCompatibilityTest`.
- **Add a new AI model provider** — builder + `*AdapterProvider` in `camunda-process-test-langchain4j`,
  registered via a `META-INF/services` SPI file.

