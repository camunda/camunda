# Camunda 8 - Orchestration cluster ArchUnit tests

This module contains [ArchUnit](https://www.archunit.org/) tests for the Camunda 8 orchestration cluster, ensuring that the production code adheres to architectural rules and best practices.
These tests can also enforce certain coding standards that prevent bugs like [RequireKebabCaseInConditionalOnPropertyArchTest](./src/test/java/io/camunda/archunit/RequireKebabCaseInConditionalOnPropertyArchTest.java).

To run these tests, you can use the following command:

```sh
./mvnw test -pl :camunda-archunit-tests
```

For more information on how to write ArchUnit tests, see the [ArchUnit documentation](https://www.archunit.org/userguide/html/000_Index.html).
