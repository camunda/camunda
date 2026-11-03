---
name: mutation-testing
description: Run PIT mutation tests on Distributed Systems code (zeebe/dynamic-config, journal, raft, snapshot, backup, scheduler, transport) and interpret the results. Use to run or scope PIT or `pitest`, read `mutations.xml`, judge surviving or NO_COVERAGE mutants, prove a test kills a mutant, or review changed code for untested behaviour.
---

# Mutation testing

PIT changes small things in production code (flips a condition, drops a call, returns a default)
and runs the tests. A mutant no test notices is a survivor and marks behaviour the tests do not
pin down. Run PIT on a scoped set of classes, query the report with a script, decide what each
survivor means, and prove every claimed kill. Human-facing overview and property table:
[`docs/testing/mutation-testing.md`](../../../docs/testing/mutation-testing.md).

## Where PIT is available

- Gradle projects that apply `buildlogic.mutation-testing-conventions`: the Distributed Systems
  production modules (`zeebe/atomix`, `backup`, `backup-stores`, `broker-client`, `broker`,
  `dynamic-config`, `dynamic-node-id-provider`, `journal`, `logstreams`, `restore`,
  `restore-standalone`, `scheduler`, `snapshot`, `stream-platform`, `transport`). Find the
  project path in `settings.gradle.kts` (`registerProject`); `zeebe/dynamic-config/impl` is
  `:zeebe-cluster-config-impl`. A project without a `pitest` task is not set up, so say so.
- Only `:zeebe-cluster-config-api` and `:zeebe-cluster-config-impl` default to a whole-module run.
  Every other project fails fast without `-Ppit.targetClasses`.
- PIT is never bound to `check` or `verify`. Only the `main` branch has it; stable branches do not.

## Run it

Gradle is the default for agents:

```bash
./gradlew --console=plain -Pskip.fe.build :<project>:pitest \
  -Ppit.targetClasses='<fqcn>,<fqcn>$*' \
  -Ppit.targetTests='<test-fqcn>,<test-fqcn>'
```

- Add `fqcn$*` so nested and anonymous classes are mutated too. Values are comma-separated globs.
- Always give both `targetClasses` and `targetTests`. Without `targetTests` PIT selects the tests
  with the `targetClasses` globs, which a class glob like `io.x.Foo` does not match `io.x.FooTest`
  with: no test runs and every mutant reports `NO_COVERAGE` (verified on a dynamic-config class:
  34 mutants, 0 tests run).
- Maven equivalent (`-Dquickly` skips tests, so add `-DskipTests=false`):
  `./mvnw -pl <module> test-compile org.pitest:pitest-maven:mutationCoverage -Dquickly -DskipTests=false -Dpit.targetClasses=... -Dpit.targetTests=...`
- Other properties (`pit.threads`, `pit.timeoutConstant`, `pit.engine`, `pit.crossModule`) are in
  the docs table; you rarely need them. Never lower the timeout (see
  [noise and pitfalls](references/survivors.md#noise-and-pitfalls)).
- Cross-module: `:zeebe-cluster-config-impl` also mutates the `-api` classes, using impl's tests.
  To test an api class with impl's tests, run impl. With Maven both modules must be in the
  reactor (`-pl zeebe/dynamic-config/api,zeebe/dynamic-config/impl`).
- Never combine the `pitest` task with `--isolated-projects`.
- Reports: Gradle `<module>/build/reports/pitest/`, Maven `<module>/target/pit-reports/`
  (`mutations.xml` and `index.html`).

## Scope and cost

Measured on 16 cores with 8 threads:

| Run                                                                  | Mutants          | Time                           |
| -------------------------------------------------------------------- | ---------------- | ------------------------------ |
| `:zeebe-cluster-config-api`, whole module                            | 1,166            | about 1.5 min                  |
| `:zeebe-cluster-config-impl`, whole module (default, cross-module)   | 3,390            | about 30 min                   |
| `:zeebe-cluster-config-impl`, whole module, `-Ppit.crossModule=false` | 2,224            | about 20 min                   |
| one class and its tests                                              | tens to hundreds | seconds to a couple of minutes |

Pick the scope from what matters, not the whole module:

1. **Classes**: the changed or target classes.
2. **Tests**: discover the ones that exercise them. The same-named `*Test` first (PIT does not
   run `*IT`), then tests in the project that use the class directly (`grep` the class name under
   `src/test`), then tests of its callers. Read enough to know which assert on the behaviour.
3. **Project**: PIT only runs the tests of the project it runs on. A class covered mostly by
   another project's tests needs a run on that project (cross-module for dynamic-config).

Estimate before running: a few hundred mutants with a focused test set take a couple of minutes.
Narrow a run that is clearly too slow instead of waiting. Drop slow tests that only mention the
class in passing, never the same-named test.

## Read the results

`mutations.xml` is large (about 2 MB for dynamic-config impl). Never read it whole; query it with a
short script. Each `<mutation>` has the attributes `status`, `detected`, `numberOfTestsRun` and the
children `sourceFile`, `mutatedClass`, `mutatedMethod`, `methodDescription`, `lineNumber`,
`mutator`, `description`, `killingTest`. This lists the open mutants by class:

```bash
python3 -I - <<'PY'
import collections, xml.etree.ElementTree as ET
by_class = collections.defaultdict(list)
for m in ET.parse("zeebe/dynamic-config/api/build/reports/pitest/mutations.xml").getroot():
    if m.get("status") in ("SURVIVED", "NO_COVERAGE"):
        by_class[m.findtext("mutatedClass")].append(
            (int(m.findtext("lineNumber")), m.get("status"),
             m.findtext("mutator").rsplit(".", 1)[-1], m.findtext("description")))
for cls, ms in sorted(by_class.items(), key=lambda kv: -len(kv[1])):
    print(len(ms), cls)
    for line, status, mutator, desc in sorted(ms)[:5]:
        print(f"   {line} {status} {mutator}: {desc}")
PY
```

Statuses:

- `KILLED`: a test failed; the behaviour is pinned down.
- `SURVIVED`: tests ran and passed. A test gap or an equivalent mutant.
- `NO_COVERAGE`: no selected test executes the line. Check that you selected the right tests.
- `TIMED_OUT`: counts as detected, but is weak evidence. In asynchronous code it flips with
  `SURVIVED` between identical runs.

The identity of a mutant is `class|method|methodDescription|mutator|description`; use the line
number only as a tie-breaker, because it drifts between commits. Identical mutants can occur on one
line (for example two equal calls), so count occurrences instead of collapsing them. When several
reports cover the same class (cross-module), a mutant killed in any report counts as killed; it is
open only if it is open in every report that contains it.

## Interpret survivors

For each survivor decide one of: test gap, equivalent, low value, or real bug. The heuristics for
equivalent mutants, what is low value, which survivors are safety-relevant in DS code, and the
noise to expect are in [references/survivors.md](references/survivors.md). Read it before judging
a survivor.

## Prove a kill

A mutant is killed by your test only when a targeted re-run (same class and test scope) reports
that exact mutant as `KILLED` with the new or extended test as its `killingTest`, where it was
`SURVIVED` or `NO_COVERAGE` before. `TIMED_OUT` is not proof. A mutant you could not prove killed
is still open: say so.

Tests follow the conventions in `AGENTS.md`: JUnit 5, `should...` names, `// given`, `// when`,
`// then`, AssertJ, Awaitility instead of `Thread.sleep`, unique IDs per test, and assertions on
observable behaviour. Extend the existing test class for the production class instead of creating
a parallel one. Never weaken, delete or disable an existing test.

## Report findings

Keep each finding short: one sentence that names the code change the mutant makes and why no test
notices it, then the tests to add (scenario and the assertion that fails under the mutation). No
heading, no mutator name, and no restating of what goes wrong under the mutation. For example:

> Changing `newPriority < 1` to `newPriority <= 1` survives: no test calls `init` with
> `newPriority == 1`, `0`, or a negative value.
>
> Suggest adding tests to `PartitionReconfigurePriorityApplierTest` that:
> - call `init` with `newPriority = 1` and assert the result is `Either.right(...)` (accepted),
>   killing this boundary mutant, and
> - call `init` with `newPriority = 0` (or negative) and assert `Either.left` with the
>   `IllegalArgumentException` message, covering the new guard itself (currently it has no
>   dedicated test at all).

Outside a line-anchored review comment, prefix each finding with its `Class:line`. Group by
class, most serious first. State what was run once for the whole report (project, target
classes, tests, mutant counts, engine) so the result can be reproduced, and list what you judged
equivalent or low value with the reason, one line each.
