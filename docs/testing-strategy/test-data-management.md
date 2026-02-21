# Test Data Management

> Back to [Testing Strategy](./README.md)

## Principles

1. **Each test creates its own data** — never depend on data from another test
2. **Use unique identifiers** — prefix test data with test method name or unique key to prevent collisions in parallel execution
3. **Clean up after yourself** — integration tests must clean up created data in `@AfterEach` or use unique prefixes that don't collide

## Patterns

**Builder/factory pattern for test data:**
```java
// CORRECT
var process = Bpmn.createExecutableProcess("test-" + testInfo.getDisplayName())
    .startEvent()
    .userTask("task-1")
    .endEvent()
    .done();

// INCORRECT — shared, mutable, reused across tests
static final BpmnModelInstance PROCESS = Bpmn.createExecutableProcess("shared-process")...;
```

**Instancio for random but reproducible data:**
```java
var user = Instancio.of(UserEntity.class)
    .set(field(UserEntity::getEmail), "test@example.com")
    .create();
```
