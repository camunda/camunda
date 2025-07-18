# Testing strategy

The following document describes the testing strategy of the Camunda mono repository.

After reading this guide the reader should understand what kinds of tests exist, how to write tests,
and where to write them, if necessary.
It can be seen as a reference for engineers and outside collaborators.

> [!Note]
>
> If you detect anything incomplete/wrong, please open PR or issue to get this fixed.

## General overview - Testing pyramid

> The “Test Pyramid” is a metaphor that tells us to group software tests into buckets of different
> granularity. It also gives an idea of how many tests we should have in each of these groups.
> https://martinfowler.com/articles/practical-test-pyramid.html

It has different notations, often depending on the context. But the general gist is that you should
have:

* unit tests, quick in execution, testing components in isolation
* integration tests, slower in execution, testing components with dependencies
* E2E or acceptance tests, slow, testing a complete user journey or a feature end-to-end (E2E)
* Reliability testing, validating the reliability and performance of the system/platform

![testing-pyramid](./assets/testing-pyramid.png)

## General conventions

Conventions are here to help make the code base clearer and consistent, we want to stick to the conventions until there are good reasons not to.

- Every public change should be verified via an automated test.
  - Every bug fix should be verified via a regression test.
- Make sure to test one behavior at a time, not combining properties or behaviors
  - If you test names that contain something like `And`, `Or`, etc., this is likely a code smell.
- Prefix your tests with `should...`
  - This is to ensure consistency in our code base and make sure that you're testing a behavior.
- Divide your test into separate sections, using comments: `given, when, then`.
  - This is to make the test clearer, separate concerns, and to highlight what exactly is tested. Future readers will appreciate it.
- It's perfectly fine to repeat yourself in tests; the focus is on readability.
  - One caveat here is with acceptance tests, where set up can be expensive.
- Where possible, use JUnit 5 to write tests.
  - When modifying an existing junit 4 test, if possible, first take the time to migrate it to junit 5.
  - If it is too time-consuming to migrate (e.g., uses lots of custom JUnit 4 rules), you can omit that.
- Where possible, avoid waiting for something to happen; this will reduce flakiness.
  - If you have to, use [Awaitility](http://www.awaitility.org/) to await conditions, **do not use `Thread.sleep` or the likes**.
- Avoid using any shaded dependencies*, and use direct ones.

_*Shaded dependency: is a dependency that is repackaged in a different dependency/library. For example, Testcontainer does this with Awaitility, to be independent of version updates. We should ensure that we do not use such shaded dependencies, as we would be conflicting with versions and usage if we would use the shaded and the explicit ones._

[//]: # (### Add rules of thumb when writing tests, e.g. use junit 5 where possible, etc.)

## Unit tests

Unit tests (UTs) are small-scope and fast, validating/verifying the behavior of just one component or unit, without dependencies.
Details, general recommendations, and how-tos/examples of how to write them, you can find in the [unit test guide](./testing/unit.md).

## Integration test

### What is an integration test

### When should I write an integration test

### Where should I write an integration test

### Do's and Don'ts

Best practices and Camunda-specific guidelines.

#### Do's: await export of necessary test data

When writing tests that rely on data that needs to be exported after e.g. a process deployment or
process instance creation, verify that the export has happened at the end of the test setup to avoid flakiness.

Here is a shortened example from [UserTaskAuthorizationIT](../qa/acceptance-tests/src/test/java/io/camunda/it/auth/UserTaskAuthorizationIT.java)
that verifies the process definition necessary for (some of) the test cases to run has been exported
successfully before running the tests.

```java
/** This is called at the end of the @BeforeAll setup **/
 private static void waitForProcessAndTasksBeingExported(final CamundaClient camundaClient) {
  Awaitility.await("should receive data from ES")
    .atMost(Duration.ofMinutes(1))
    .ignoreExceptions() // Ignore exceptions and continue retrying
    .untilAsserted(
      () -> {
        assertThat(
        camundaClient
          .newProcessDefinitionSearchRequest()
          .filter(filter -> filter.processDefinitionId(PROCESS_ID_1))
          .send()
          .join()
          .items())
        .hasSize(1);
      });
   }
```

## Acceptance test

> [!Note]
>
> Formerly known as QA - Tests

[//]: # (If people ever wonder about this note, then we can remove it.)

Acceptance tests (ATs) are mostly slow, testing a complete user journey or a feature end-to-end (E2E), covering multiple or even all components at the same time in an integrated manner.
Details, general recommendations, and how-tos/examples of how to write them, you can find in the [acceptance test guide](./testing/acceptance.md).

## Reliability testing

We define reliability testing as a type of software testing and practice that validates system performance and reliability. It can thus be done over time and with injection failure scenarios (injecting chaos).

Details, about the current available practices, tools, and infrastructure you can find in the [reliability testing guide](testing/reliability-testing.md).
