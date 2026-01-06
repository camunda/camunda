# Camunda-Process-Test - DSL

A DSL to describe test scenarios for Camunda Process Test in a JSON format.

The structure of the DSL is defined in the JSON
Schema: [test-scenario-dsl.schema.json](src/main/resources/schema/test-scenario-dsl.schema.json).

A test scenario JSON file can be deserialized with [Jackson](https://github.com/FasterXML/jackson)
into a Java object of the
type [TestScenario.java](src/main/java/io/camunda/process/test/api/dsl/TestScenario.java).

