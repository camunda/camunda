---
applyTo: "testing/**"
---

# Camunda Process Test (CPT) — Agent Guidance

You are working in `testing/`, the **Camunda Process Test** libraries (owner `@camunda/c8-testing`).

**Read [`testing/CONTRIBUTING.md`](../../testing/CONTRIBUTING.md) first** — it is the canonical
guide (module layout + dependency graph, architecture, testing conventions, CI, release management,
and step-by-step how-tos). [`testing/AGENTS.md`](../../testing/AGENTS.md) has a condensed version of
the same. Treat `CONTRIBUTING.md` as the single source of truth; update it when conventions change
rather than duplicating recipes elsewhere.

## Essentials

- Modules (see `testing/pom.xml`): `camunda-process-test-java` (core), `-json-test-cases`,
  `-coverage`, `-langchain4j`, `-spring`, `-spring-boot-3`, `-spring-boot-4` (relocated), `-example`.
- Keep the public `io.camunda.process.test.api` vs `.impl` split.
- Add Spring features to `camunda-process-test-spring`; `spring-boot-3`/`spring-boot-4` are
  packaging/compatibility only.
- `json-test-cases` holds no runtime logic; keep the JSON schema and Java interfaces in sync
  manually and extend `PojoCompatibilityTest`.
- Prefer unit tests; name integration tests `*IT`; ITs need Docker (Testcontainers).

## Build

```bash
./mvnw install -pl testing/camunda-process-test-java -am -Dquickly -T1C
./mvnw verify  -pl testing/camunda-process-test-java -DskipTests=false -DskipITs -Dquickly
# Before committing (formatting also covers markdown):
./mvnw license:format spotless:apply -T1C
```

## How-tos

Step-by-step guides live in
[`CONTRIBUTING.md`](../../testing/CONTRIBUTING.md#7-common-contributions-how-to): adding a new
assertion, a new JSON test-case instruction, and a new AI model provider.
