# Camunda-Process-Test - JSON Test Cases

A JSON format to describe test cases for Camunda Process Test.

The structure of the test cases is defined in the JSON
Schema: [schema/cpt-test-cases/schema.json](src/main/resources/schema/cpt-test-cases/schema.json).

A test cases JSON file can be deserialized with [Jackson](https://github.com/FasterXML/jackson)
into a Java object of the
type [TestCases.java](src/main/java/io/camunda/process/test/api/testCases/TestCases.java). The Java
model uses [Immutables](https://immutables.github.io/).

> [!NOTE]
> The JSON schema and the Java interfaces are kept in sync **manually** (the code is not generated
> from the schema). [`PojoCompatibilityTest`](src/test/java/io/camunda/process/test/testCases/PojoCompatibilityTest.java)
> verifies that both stay compatible.

Test cases are executed by the runner in
[camunda-process-test-java](../camunda-process-test-java).

## Contributing

See the [testing contribution guide](../CONTRIBUTING.md), which includes a step-by-step guide for
adding a new JSON test-case instruction.

