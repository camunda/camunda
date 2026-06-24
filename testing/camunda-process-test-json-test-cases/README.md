# Camunda-Process-Test - JSON Test Cases

A JSON format to describe test cases for Camunda Process Test.

The structure of the test cases is defined in the JSON
Schema: [schema/cpt-test-cases/schema.json](src/main/resources/schema/cpt-test-cases/schema.json).

A test cases JSON file can be deserialized with [Jackson](https://github.com/FasterXML/jackson)
into a Java object of the
type [TestCases.java](src/main/java/io/camunda/process/test/api/testCases/TestCases.java).

