# Unit tests

A test that validates/verifies the behavior of just one component or unit, without dependencies, is
a unit test.

> [!Note]
>
> A unit or component can be just one class, but doesn't necessarily need.

**Key properties:**

* Small-scope:
  * One unit test should validate one behavior
  * Testing without dependencies
* Fast: A unit test should be quick in execution
  * Comes with a small scope
* Most of our tests should be unit tests (see testing pyramid above)
  * As they should be fast and small-scope, we can have many of them

> [!Note]
>
> The key properties are rule of thumb, we should try to stick with it, there might be cases to
> break with this.
>
> [!Note]
>
> **It is fine to repeat yourself in tests.**
>
> It is also highly recommended to improve the readability and understandably of such tests. Giving
> the full context to the reader.
>
> The expectation is that for unit tests that set up code is fast, and test should be itself fast.
> If this is not the case, there might be something off with your test itself.

## Small-scope

**Why:**

* To separate concerns
  - To make sure we test on the component/unit in isolation, building confidence that this unit itself
    behaves correctly. The test behavior is more controllable and observable
  - A test failure shows specifically that one unit and behavior is affected. If not small-scoped, we might
    not test one unit, and can't be sure whether any other behavior influences the test
* Allows to be quick execution time, when focusing on one small unit with little setup
* Reduces the maintainability/readability
  - as tests are not affected by changes in other components
  - It makes it clear and understandable what is tested
* We can separately test business logic from the integration with asynchronous code

### Do's:

```java
@Test
public void shouldAddSomeValues() {
  // given
  // .. set up component/unit/class
  final var calculator = new Calculator();
  final var expectedValue = 7;

  // when
  // .. trigger some behavior
  final var actualValue = calculator.add(2, 5);

  // then
  // .. validate result / behavior
  assertThat(actualValue).isEqualTo(expectedValue);
}
```

The test is a unit test because:

* It is small scoped
  * it tests one behavior - addition
  * it has no dependencies
* Side-effect: It is likely to be quick

### Don'ts: Usage of dependency

```java
@Test
public void shouldReturnSuccessfulResponse() {
  // given
  // .. set up database
  // .. set up client

  // when
  final var response = client.sendRequest();

  // then
  assertThat(response.statusCode).isEqualTo(200);
}
```

This test is not a unit test, because:

* It is not small scoped:
  * it tests one behavior, but it depends on other components to run
* Test is an integration test, as we are integrating with a dependency.
  * It might be flaky due to the dependency
  * The dependency might be not fully under control, which means there might be many cases we can't
    test in such a test
* It may also be slow, setting up the database

### Don'ts: Testing multiple behaviors

```java
@Test
public void shouldAddAndSubtract() {
  // given
  // .. set up component/unit/class
  final var calculator = new Calculator();
  final var expectedValue = 5;

  // when
  // .. trigger some behavior
  final var actualValue = calculator.add(2, 5);

  // then
  // .. validate result / behavior
  assertThat(actualValue).isEqualTo(expectedValue);

  // when
  // .. trigger some behavior
  final var anotherValue = calculator.sub(2, actualValue);

  // then
  // .. validate result / behavior
  assertThat(anotherValue).isEqualTo(expectedValue);
}
```

This test is **not a good** unit test
