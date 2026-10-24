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
- Do not use `-Pquickly`: it disables tests. To run tests, run `test` (or `it`). For the
  distribution archive, run the commands in "Common commands".

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
./gradlew buildDistball                                # dist tar incl. frontend build
./gradlew buildDistballZip                             # dist zip incl. frontend build
./gradlew -Pskip.fe.build=true buildDistball           # backend-only dist, skips frontend
./gradlew -p gradle/build-logic test                   # build-logic tests (not run by CI)
```

## Properties

- `-PuseMavenLocal`: add Maven Local to resolution. Only when needed.
- `-Ptest.max.forks=N`: test JVM forks. Keep the default `1`: forks of one task share a port
  range and can hit bind failures. Update this once per-worker port isolation is merged.
- `-Ptest.jvm.maxheap=2g`, `-Pparallel.tests`, `-Pjunit.thread.count=N`.
- `-PincludeSlowTests`, `-PincludePerformanceTests`, `-PincludeStraceTests`,
  `-PincludeRandomTests`.
- Build and configuration caches are on. Check `UP-TO-DATE` / `FROM-CACHE` with `--info`.
  Some tasks need `--no-configuration-cache` (e.g. `writeDistDependencyReport`).

## Gotchas

- Frontend builds run only as part of `dist` assembly, not compile or tests. An archive built
  with `-Pskip.fe.build=true` is not frontend-complete: do not ship it.
- Parity checks against Maven need the Maven ZIP built from the same revision.
- The build logs only failed tests, not passed ones. If the `test` task ran (not `UP-TO-DATE` or
  `FROM-CACHE`), `BUILD SUCCESSFUL` with no test output means the tests passed. To confirm a
  filter matched, check `tests="N"` in `<project>/build/test-results/test/TEST-*.xml`. These
  reports can be left over from an earlier run if the task did not execute.
- Rerunning the same test with unchanged inputs is `UP-TO-DATE` and does nothing. Add `--rerun`
  to force only that task, not its dependencies, so compile tasks stay up to date:
  `./gradlew :<project>:test --tests <Class> --rerun`.
- Add `-i` to see test standard output. Each test's output starts with a header such as
  `ProcessEngineMetricsTest > shouldTrackActiveProcessInstanceGauge STANDARD_OUT`. It also prints
  Gradle's own `INFO` logs, so the output is noisy: filter it (e.g. `| grep ...`) or send it to a
  file. Alternative without `-i`: the `<system-out>` element of
  `<project>/build/test-results/test/TEST-*.xml` holds the same test output, for the whole class.
