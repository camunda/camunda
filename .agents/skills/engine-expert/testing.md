# Engine Testing

The engine has two test entry points: `ProcessingStateExtension` (JUnit 5) for state and applier tests, and `EngineRule` (JUnit 4) for processor tests. Pick the smallest entry point that exercises what you're changing — `EngineRule` is significantly more expensive than `ProcessingStateExtension`.

## Iron rules

- **Always short-circuit `RecordingExporter`** with `.limit()`, `.getFirst()`, `.exists()`, etc. Otherwise the stream stays open and the test hangs.
- **Verify behavior via appended records, not state.** In `EngineRule` tests, query the `RecordingExporter` to assert what the engine produced. Do **not** assert on state classes from an `EngineRule` test:
  - State is an implementation detail; the appended records are the engine's actual output and the contract with consumers.
  - State classes are not thread-safe; reading them from the test thread while the engine writes is unsafe.
  - State-only assertions are appropriate in `ProcessingStateExtension` tests for state/applier behavior, not in `EngineRule` tests.
- **Tests must be fast AND reliable.** Re-run any new or modified test **at least 3 times** before declaring it stable. Flaky tests will erode trust in the suite.
- **`EngineRule` is expensive — default to per-class.** Use per-test isolation only when the test genuinely cannot share state with its siblings.
- **Use `// given / // when / // then`** structure, AssertJ assertions, and Awaitility for async waiting (never `Thread.sleep`).
- **Migrate JUnit 4 → 5** when modifying older tests; don't add new JUnit 4 tests outside `EngineRule`-based ones (which still require JUnit 4).

## Patterns

### State + applier tests (JUnit 5)

Use `ProcessingStateExtension`. Assert on state directly here — that's the unit under test.

- Templates:
  - `zeebe/engine/src/test/java/io/camunda/zeebe/engine/state/user/UserStateTest.java`
  - `zeebe/engine/src/test/java/io/camunda/zeebe/engine/state/appliers/TenantAppliersTest.java`

### Processor tests (JUnit 4)

Use `EngineRule`. Assert on appended records via `RecordingExporter` — never on state.

- Templates:
  - **Per-class (default):** `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/user/CreateUserTest.java` — uses `@ClassRule public static final EngineRule ENGINE`. Match this pattern unless tests genuinely cannot share engine state.
  - **Per-test (exception):** `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/job/JobCompleteAuthorizationTest.java` — uses an instance `@Rule EngineRule`. Useful reference for record assertions and test-client usage; only mirror the per-test isolation when siblings can't share state.
- Internal test clients (`engine.job()`, `engine.user()`, `engine.authorization()`, etc.) are the entry points for submitting commands.
- When adding a new feature, **extend the corresponding test client** rather than reaching into private engine internals. Template: `zeebe/engine/src/test/java/io/camunda/zeebe/engine/util/client/JobClient.java`.

## Debugging

- `CompactRecordLogger` output is the first thing to read on a failed test. Add `assert false` at the end of a passing test to dump records during exploration.
- **If `CompactRecordLogger` omits a field that mattered to your investigation, that's a strong signal to extend the logger** for that record type. Future debugging benefits — your time is not the only time saved.
- Source: `zeebe/test-util/src/main/java/io/camunda/zeebe/test/util/record/CompactRecordLogger.java`

## Running tests locally

Always scope to the test(s) you actually need — running the full engine module locally is slow and is what CI is for.

```bash
# Single class
./mvnw verify -pl zeebe/engine -Dtest=MyTest \
  -DskipTests=false -DskipITs -Dquickly

# Multiple classes
./mvnw verify -pl zeebe/engine -Dtest=MyTest1,MyTest2 \
  -DskipTests=false -DskipITs -Dquickly

# Glob pattern
./mvnw verify -pl zeebe/engine -Dtest='*UserTest*' \
  -DskipTests=false -DskipITs -Dquickly
```

`-Dquickly` skips checks (spotless, license) and other tests, which is what makes the run fast. Apply spotless separately via the format command in `SKILL.md` § "Local checks before commit". CI runs the comprehensive build.

## Canonical docs

- `docs/testing.md`, `docs/testing/unit.md` — repo-wide testing guidelines.
- `zeebe/engine/README.md` § "Testing guidelines" — engine-specific test patterns and the `CompactRecordLogger` failure-output legend.

