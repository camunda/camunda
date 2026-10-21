---
name: gradle-run-build
description: Use when compiling, testing, or packaging the monorepo with ./gradlew. Not for editing the Gradle build (use gradle-build-parity).
---

# Running the Gradle build

The Gradle build is experimental; Maven stays authoritative. Full reference:
[`gradle/README.md`](../../../gradle/README.md) — read it when this skill is not enough, and
update both together. Run everything from the repo root.

## Rules

- Project paths come from `settings.gradle.kts` (`registerProject`), not directories:
  `zeebe/engine` is `:zeebe-workflow-engine`. Look the name up before guessing.
- Scope to one project (`:project:task`). Never run root-level `test`.
- Essentially never run `build`: it launches everything (all tests, checks, assembly).
- `test` = unit tests (Surefire). `it` = `IT*`, `*IT`, `*ITCase` (Failsafe). Same source set.
- `check` depends on `it`, so `check` runs unit and integration tests.
- Never use `-Ptest.max.retries` to hide failures.

## Single test: use the exact class name

```bash
# Fast: Gradle resolves the class directly
./gradlew :zeebe-workflow-engine:test --tests ProcessEngineMetricsTest

# Slow on big modules: wildcard forces Gradle to scan and load many test classes
./gradlew :zeebe-workflow-engine:test --tests '*ProcessEngineMetricsTest'
```

Prefer a simple class name or fully qualified name over a leading `*` pattern. On large
modules such as `zeebe/engine`, the wildcard greatly delays test start. Measured wall time for
`ProcessEngineMetricsTest` in `:zeebe-workflow-engine`: simple or fully qualified name about
5s; `'*ProcessEngineMetricsTest'` 16–27s (about 3–4x slower). Use the wildcard only
when the class name is ambiguous.

Run one test case (method) by appending `.methodName`:

```bash
./gradlew :zeebe-workflow-engine:test \
  --tests ProcessEngineMetricsTest.shouldIncreaseProcessInstanceCreatedAtDefaultStartEvent
```

IT classes: same filter on the `it` task.

## Common commands

```bash
./gradlew testClasses                                  # compile all main+test
./gradlew :<project>:compileTestJava                   # compile one project
./gradlew :<project>:test --tests <Class>              # unit test
./gradlew :<project>:it --tests <Class>                # integration test
./gradlew :camunda-zeebe:distZip                       # dist incl. frontend build
./gradlew -Pskip.fe.build=true :camunda-zeebe:distZip  # backend-only dist, skips frontend
./gradlew -p gradle/build-logic test                   # build-logic tests (not run by CI)
```

## Properties

- `-Pquickly`: drops Optimize projects, **disables tests and Spotless**. Use only for compile
  checks.
- `-PuseMavenLocal`: add Maven Local to resolution. Only when needed.
- `-Ptest.max.forks=N` (test JVM forks), `-Ptest.jvm.maxheap=2g`, `-Pparallel.tests`,
  `-Pjunit.thread.count=N`.
- `-PincludeSlowTests`, `-PincludePerformanceTests`, `-PincludeStraceTests`,
  `-PincludeRandomTests`.
- Build and configuration caches are on. Check `UP-TO-DATE` / `FROM-CACHE` with `--info`.
  Some tasks need `--no-configuration-cache` (e.g. `writeDistDependencyReport`).

## Gotchas

- Frontend builds run only as part of `dist` assembly, not compile or tests. An archive built
  with `-Pskip.fe.build=true` is not frontend-complete: do not ship it.
- Parity checks against Maven need the Maven ZIP built from the same revision.
- The build logs only failed tests, not passed ones. `BUILD SUCCESSFUL` with no test output means
  the tests passed. To confirm a filter matched, check `tests="N"` in
  `<project>/build/test-results/test/TEST-*.xml`.
- Add `-i` to see test standard output. Each test's output starts with a header such as
  `ProcessEngineMetricsTest > shouldTrackActiveProcessInstanceGauge STANDARD_OUT`. It also prints
  Gradle's own `INFO` logs, so the output is noisy: filter it (e.g. `| grep ...`) or send it to a
  file. Alternative without `-i`: the `<system-out>` element of
  `<project>/build/test-results/test/TEST-*.xml` holds the same test output, for the whole class.
