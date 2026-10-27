# Testing

Camunda's testing libraries for processes and process applications — **Camunda Process Test** (CPT).

## Modules

|                                    Module                                    |                                                  Description                                                   |
|------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| [camunda-process-test-java](camunda-process-test-java)                       | Core library: JUnit 5 extension, runtime, assertions, utilities, AI assertions, and the JSON test-case runner. |
| [camunda-process-test-json-test-cases](camunda-process-test-json-test-cases) | JSON test-case format (schema + Java model).                                                                   |
| [camunda-process-test-coverage](camunda-process-test-coverage)               | Process coverage report (Java backend + BPMN visualisation frontend).                                          |
| [camunda-process-test-langchain4j](camunda-process-test-langchain4j)         | LangChain4j bridge for LLM-as-a-judge and semantic-similarity assertions.                                      |
| [camunda-process-test-spring](camunda-process-test-spring)                   | Spring integration (Spring Boot 4 / Spring 6).                                                                 |
| [camunda-process-test-spring-boot-3](camunda-process-test-spring-boot-3)     | Spring integration repackaged for Spring Boot 3.5.x.                                                           |
| [camunda-process-test-spring-boot-4](camunda-process-test-spring-boot-4)     | Relocation stub → `camunda-process-test-spring` (migration path from 8.7/8.8).                                 |
| [camunda-process-test-example](camunda-process-test-example)                 | Example project for demos and documentation snippets.                                                          |

Read more about the libraries and how to get started in our
documentation: https://docs.camunda.io/docs/apis-tools/testing/getting-started/.

See the [contribution guide](CONTRIBUTING.md) for details on how to contribute to the testing
libraries.
