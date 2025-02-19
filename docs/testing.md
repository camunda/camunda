# Testing strategy

The following document describes the testing strategy of the Camunda mono repository.

After reading this guide the reader should understand what kinds of tests exist, how to write tests, and where to write them, if necessary.
It can be seen as a reference for engineers and outside collaborators.

> [!Note]
>
> If you detect anything incomplete/wrong, please open PR or issue to get this fixed.
>


## General overview - Testing pyramid

> The “Test Pyramid” is a metaphor that tells us to group software tests into buckets of different granularity. It also gives an idea of how many tests we should have in each of these groups.
> https://martinfowler.com/articles/practical-test-pyramid.html

It has different notations, often depending on the context. But the general gist is that you should have:

 * unit tests, quick in execution, testing components in isolation
 * integration tests, slower in execution, testing components with dependencies
 * E2E or acceptance tests, slow, testing a complete user journey or a feature end-to-end (E2E)

![testing-pyramid](./assets/testing-pyramid.png)


## Unit tests



### What is a unit test

### When/Where should I write the unit test

## Integration test

### What is an integration tests

### When should I write an integration test

### Where should I write an integration test

## Acceptance test

> [!Note]
>
> Formerly known as QA - Tests

### What is an acceptance test

### When should I write an acceptance test

### Where should I write an integration test
