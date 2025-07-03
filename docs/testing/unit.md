# Unit tests

[//]: # (### What is a unit test)

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

### Dont's: Usage of dependency

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

### Dont's: Testing multiple behaviors

```java

@Test
public void shouldAddAndSubstract() {
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

This test is **not a good** unit test, _because_:

* It is not small scoped:
  * it tests multiple behaviors at once
    * Good indicator of such, is the `And` in the name
  * it has a dependency, to an early behavior of the component
    * In general this might be fine, if multiple tests exist to validate the different behaviors
      individually

**How to improve this:**

* Add new tests for `Add` and `Substract` individually
* Then combine behaviors if necessary
* Rename test to `shouldSubstractAfter`

```java

@Test
public void shouldAdd() {
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

@Test
public void shouldSubstract() {
  // given
  // .. set up component/unit/class
  final var calculator = new Calculator();
  final var expectedValue = 5;

  // when
  // .. trigger some behavior
  final var value = calculator.sub(7, 2);

  // then
  // .. validate result / behavior
  assertThat(value).isEqualTo(expectedValue);
}

@Test
public void shouldSubstractAfterAdd() {
  // given
  // .. set up component/unit/class
  final var calculator = new Calculator();
  final var expectedValue = 5;
  final var actualValue = calculator.add(2, 5);

  // when
  // .. trigger some behavior
  final var anotherValue = calculator.sub(2, actualValue);

  // then
  // .. validate result / behavior
  assertThat(anotherValue).isEqualTo(expectedValue);
}
```

### Dont's: Testing asynchronous code

If we do not separate asynchronous code and the business logic, we have to test our code asynchronously.
This means we have a harder time testing our logic, as they are non-deterministic and are likely to become flaky.

The tests are no longer small-scoped and have a dependency on, for example, a scheduler/executor, etc.

Good examples of the separation of business logic and asynchronous code to test synchronously.

* [Asynchronous code](https://github.com/camunda/camunda/blob/main/zeebe/transport/src/main/java/io/camunda/zeebe/transport/stream/impl/RemoteStreamTransport.java), where the wiring of the business logic happens
* [Business code](https://github.com/camunda/camunda/blob/main/zeebe/transport/src/main/java/io/camunda/zeebe/transport/stream/impl/RemoteStreamApiHandler.java), which can be separately tested
* [Unit tests](https://github.com/camunda/camunda/blob/main/zeebe/transport/src/test/java/io/camunda/zeebe/transport/stream/impl/RemoteStreamApiHandlerTest.java) of the business code, making sure to validate each behavior

### Where should unit tests live

Typically, unit tests will live in the same module and package as the unit under test, but under the
test source folder.

For example, say you are unit testing the following class under
`module-foo/src/main/java/com/acme/foo/MyClass.java`, in the `module-foo` module. An equivalent test
class would then live in `module-foo/src/test/java/com/acme/foo/MyClassTest.java`, also in the
`module-foo` module.

As a convention, if you're unit testing a class, the equivalent test file would be the class name
with the `Test` suffix. So in our case, `MyClass` becomes `MyClassTest`.

Some examples of class unit tests:

- [DbCompositeKey](zeebe/zb-db/src/main/java/io/camunda/zeebe/db/impl/DbCompositeKey.java)
- [DbCompositeKeyTest](zeebe/zb-db/src/test/java/io/camunda/zeebe/db/impl/DbCompositeKeyTest.java)

> [!Note]
> This convention allows the surefire-maven module (which runs all the unit tests as part of our
> build pipeline) to know which classes are test classes. This allows you to also have utility
> classes in your test package which are not executed by surefire, so long as they are not suffixed
> with `Test`.

Of course, sometimes you're not testing a class, but rather multiple as one unit. In this case,
name the test class such that it conveys the logical grouping of the tests it contains.

For example, take a look at:
- [ObjectMappingDefaultValuesTest](zeebe/msgpack-value/src/test/java/io/camunda/zeebe/msgpack/ObjectMappingDefaultValuesTest.java),
which tests the serialization and deserialization of default properties in `UnpackedObject` types.
- [JsonSerializableToJsonTest](zeebe/protocol-impl/src/test/java/io/camunda/zeebe/protocol/impl/JsonSerializableToJsonTest.java),
which tests that all protocol values can be serialized to JSON.

In both cases, we are testing a few classes interacting together, but testing logically similar
behavior (e.g. JSON serialization) which warrants grouping together in the same test class.

Note that these classes still live in the same modules as the units they are testing, even if they
group multiple ones together.

## When to write a unit test

As a rule of thumb, you should always add unit tests for any public API with some business logic.
Avoid testing very simple methods (e.g. getter, setter), and focus on those with business logic.

Additionally, focus on public facing APIs only. Avoid testing private, protected, or package private
methods as these are implementation details: if something is not accessible from outside the unit,
it's essentially an implementation detail. By focusing on testing only the public behavior, we avoid
coupling the test to the implementation, making it more resilient to future changes.

> [!Note]
> Of course, if a class is package-private and used extensively in its own package, its public API
> is then its private-package level methods. Use your judgement on what is the public API of a unit.

Finally, bug fixes should always come with at least one regression test.

> [!Note]
> You can use the
> [RegressionTest](zeebe/test-util/src/main/java/io/camunda/zeebe/test/util/junit/RegressionTest.java)
> annotation where you can add a link to issue being fixed. This provides context for future
> maintainers as to why the test is here at all.

Let's illustrate this with some examples. Take one of our existing
classes, [ClientStreamImpl](zeebe/transport/src/main/java/io/camunda/zeebe/transport/stream/impl/ClientStreamImpl.java):

```java
/** Represents a registered client stream. * */
record ClientStreamImpl<M extends BufferWriter>(
    ClientStreamIdImpl streamId,
    AggregatedClientStream<M> serverStream,
    DirectBuffer streamType,
    M metadata,
    ClientStreamConsumer clientStreamConsumer)
  implements ClientStream<M> {

  @Override
  public Set<MemberId> liveConnections() {
    return serverStream().liveConnections();
  }
}
```

It would be pointless to test that the properties are returned by the getters; not only is this
logic too simple, but it's also essentially testing a Java feature, not our own code.

Additionally, testing the `#liveConnections()` method, while our own code, is also much too simple
as it simply delegates to the underlying server stream member.
