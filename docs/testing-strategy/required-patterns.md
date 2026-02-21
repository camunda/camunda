# Required Patterns

> Back to [Testing Strategy](./README.md)

---

## 1. Given/When/Then structure

Every test method must contain `// given`, `// when`, `// then` comments:

```java
@Test
void shouldRejectInvalidInput() {
    // given
    final var request = new CreateRequest(null, -1);

    // when
    final var result = assertThrows(ValidationException.class,
        () -> service.create(request));

    // then
    assertThat(result.getMessage()).contains("name must not be null");
}
```

---

## 2. Regression test annotation for bug fixes

```java
@Test
@RegressionTest("https://github.com/camunda/camunda/issues/12345")
void shouldNotCrashWhenInputIsEmpty() {
    // given — the exact scenario that caused the bug
    // when — the action that triggered the bug
    // then — the correct behavior
}
```

---

## 3. Awaitility for all asynchronous assertions

```java
Awaitility.await("process instance should be active")
    .atMost(Duration.ofSeconds(10))
    .pollInterval(Duration.ofMillis(200))
    .untilAsserted(() ->
        assertThat(getProcessInstance(key).getState()).isEqualTo(ACTIVE));
```

---

## 4. `expect.poll()` or `expect().toPass()` in Playwright

```typescript
await expect.poll(async () => {
  const response = await request.get(url);
  return response.status();
}, {timeout: 30_000, intervals: [500, 1000, 2000]}).toBe(200);
```

---

## 5. TestContainers via `TestSearchContainers` factory

```java
// REQUIRED: use the central factory
@Container
private static final ElasticsearchContainer CONTAINER =
    TestSearchContainers.createDefeaultElasticsearchContainer();

// PROHIBITED: hardcoded image tags
@Container
private static final ElasticsearchContainer CONTAINER =
    new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.18.4");
```

---

## 6. Page Object Model in Playwright

```typescript
// REQUIRED: interact through page objects
await loginPage.login({username: 'demo', password: 'demo'});

// PROHIBITED: raw selectors in test files
await page.locator('#username-input').fill('demo');
await page.locator('#password-input').fill('demo');
await page.locator('button[type="submit"]').click();
```
