# Mutation testing

[PIT](https://pitest.org) changes small things in production code (flips a condition, drops a call,
returns a default) and runs the tests. A mutant that no test notices is a _survivor_ and marks
behaviour the tests do not pin down. It complements line coverage: a line can be covered without
any assertion depending on it.

PIT is set up for the code owned by the Distributed Systems team (`zeebe/dynamic-config`, journal,
raft, snapshots, backups, scheduler, transport and so on). It is never part of `verify`, `check` or
CI test jobs; you run it on demand. A run is slow (minutes for a module), so scope it.

## Run it with Gradle

```bash
# whole module, dynamic-config only (defaults to io.camunda.zeebe.dynamic.config.*)
./gradlew :zeebe-cluster-config-api:pitest -Ppit.threads=4

# scoped run, required for every other module
./gradlew :zeebe-journal:pitest \
  -Ppit.targetClasses='io.camunda.zeebe.journal.file.SparseJournalIndex*' \
  -Ppit.targetTests='io.camunda.zeebe.journal.file.SparseJournalIndexTest'
```

|       Property        |                                 Meaning                                  |
|-----------------------|--------------------------------------------------------------------------|
| `pit.targetClasses`   | comma-separated class globs to mutate; required outside dynamic-config   |
| `pit.targetTests`     | comma-separated test class globs to run (default: derived by PIT)        |
| `pit.threads`         | worker threads (default 8)                                               |
| `pit.timeoutConstant` | timeout constant in milliseconds (PIT default)                           |
| `pit.engine`          | `gregor` (default) or `descartes` (extreme mutation, far fewer mutants)  |
| `pit.crossModule`     | `:zeebe-cluster-config-impl` also mutates the api classes (default true) |

The same properties work with Maven as `-Dpit.<name>=...`.

The report is written to `<module>/build/reports/pitest/` (`index.html`, `mutations.xml`). Do not
combine the task with `--isolated-projects`, the PIT plugin does not support it yet.

## Run it with Maven

The plugin is configured in `parent/pom.xml` and not bound to a phase. `-Dquickly` skips tests, so
pass `-DskipTests=false`.

```bash
# dynamic-config api (whole module by default)
./mvnw -pl zeebe/dynamic-config/api test-compile org.pitest:pitest-maven:mutationCoverage \
  -Dquickly -DskipTests=false -Dpit.threads=4

# dynamic-config impl mutating the api classes too: both modules must be in the reactor
./mvnw -pl zeebe/dynamic-config/api,zeebe/dynamic-config/impl test-compile \
  org.pitest:pitest-maven:mutationCoverage -Dquickly -DskipTests=false

# any other module, scoped
./mvnw -pl zeebe/journal test-compile org.pitest:pitest-maven:mutationCoverage \
  -Dquickly -DskipTests=false \
  -Dpit.targetClasses='io.camunda.zeebe.journal.file.SparseJournalIndex*' \
  -Dpit.targetTests='io.camunda.zeebe.journal.file.SparseJournalIndexTest'
```

The report is in `<module>/target/pit-reports/`. Maven and Gradle generate the same mutants for the
same module.

## Read the report

Open `index.html` and drill down to a class. In `mutations.xml` each `<mutation>` has a `status`:

- `KILLED`: a test failed, the behaviour is pinned down.
- `SURVIVED`: tests ran and passed. Either a missing assertion or an equivalent mutant (the
  mutation cannot change behaviour).
- `NO_COVERAGE`: no test executes the line.
- `TIMED_OUT`: counted as killed by PIT, but weak evidence; asynchronous code flips between
  `SURVIVED` and `TIMED_OUT` across identical runs.

To check that a new test kills a mutant, re-run PIT scoped to the class and look for that exact
mutant with your test as `killingTest`.

## Agents

The [`mutation-testing` skill](../../.agents/skills/mutation-testing/SKILL.md) teaches an agent to
scope a run, query the report and decide what a survivor means. The
[`mutation-testing-pr-review`](../../.github/workflows/mutation-testing-pr-review.md) agentic
workflow uses it for a non-blocking review of pull requests that change Distributed Systems
production code, limited to mutants on the changed lines.
