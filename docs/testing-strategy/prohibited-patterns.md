# Prohibited Patterns

> Back to [Testing Strategy](./README.md)

These patterns are **banned** from all new code and must be removed from existing code during maintenance.

---

## 1. `Thread.sleep()` in test code

```java
// PROHIBITED
Thread.sleep(5000);
assertThat(service.getStatus()).isEqualTo("READY");

// REQUIRED: use Awaitility
Awaitility.await()
    .atMost(Duration.ofSeconds(10))
    .pollInterval(Duration.ofMillis(100))
    .untilAsserted(() ->
        assertThat(service.getStatus()).isEqualTo("READY"));
```

**Exception**: `Thread.sleep()` is acceptable only in production code for deliberate backoff/throttling, never in tests.

---

## 2. `page.waitForTimeout()` in Playwright tests

```typescript
// PROHIBITED
await page.waitForTimeout(500);

// REQUIRED: use assertion-based waiting
await expect(page.locator('.modal')).toBeVisible();
```

**No exceptions.**

---

## 3. `actionTimeout: 0` in Playwright configuration

```typescript
// PROHIBITED
actionTimeout: 0,  // unlimited — will hang forever

// REQUIRED
actionTimeout: 10_000,  // bounded timeout
```

---

## 4. JUnit / Hamcrest assertions

```java
// PROHIBITED (enforced by Checkstyle)
assertEquals(expected, actual);        // JUnit
assertThat(actual, is(expected));      // Hamcrest

// REQUIRED
assertThat(actual).isEqualTo(expected);  // AssertJ
```

This is already enforced by Checkstyle (`IllegalImport` rule in `build-tools/src/main/resources/check/.checkstyle.xml`).

---

## 5. JUnit 4 in new code

```java
// PROHIBITED in new code
@RunWith(SpringRunner.class)
@Rule public final EngineRule engine = EngineRule.singlePartition();

// REQUIRED
@ExtendWith(SpringExtension.class)
@RegisterExtension final EngineExtension engine = EngineExtension.create();
```

> **Note**: For the JUnit 4 -> 5 migration plan for existing code, see [CI/CD Phase 4: Developer Experience](../ci-cd/phase-4-developer-experience.md).

---

## 6. Raw `Thread` management in concurrency tests

```java
// PROHIBITED
Thread[] threads = new Thread[5];
for (int i = 0; i < threads.length; i++) {
  threads[i] = new Thread(task);
  threads[i].start();
  Thread.sleep(10);
}

// REQUIRED
ExecutorService executor = Executors.newFixedThreadPool(5);
CountDownLatch startLatch = new CountDownLatch(1);
for (int i = 0; i < 5; i++) {
  executor.submit(() -> {
    startLatch.await();  // all threads start simultaneously
    task.run();
  });
}
startLatch.countDown();
executor.shutdown();
executor.awaitTermination(10, TimeUnit.SECONDS);
```

---

## 7. Non-deterministic test data without seed

```java
// PROHIBITED — different data each run, unreproducible failures
var randomName = UUID.randomUUID().toString();

// REQUIRED — seeded random or deterministic
var testName = "test-process-" + testInfo.getDisplayName().hashCode();

// OR use Instancio with seed
var data = Instancio.of(MyModel.class)
    .withSeed(12345L)
    .create();
```

**Exception**: UUIDs are acceptable when used for isolation (unique index names, unique process IDs) and the specific value doesn't matter for the assertion.

---

## 8. `@Disabled` without an issue link

```java
// PROHIBITED
@Disabled("it's broken")

// REQUIRED
@Disabled("Flaky, tracked in https://github.com/camunda/camunda/issues/42928")
```
