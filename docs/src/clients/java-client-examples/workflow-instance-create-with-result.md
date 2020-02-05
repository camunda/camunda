# Create a Workflow Instance and Await Result

## Prerequisites

1. Running Zeebe broker with endpoint `localhost:26500` (default)
1. Run the [Deploy a Workflow example](/clients/java-client-examples/workflow-deploy.html). Deploy [`demoProcessSingleTask.bpmn`](https://github.com/zeebe-io/zeebe/tree/{{commit}}/samples/src/main/resources/demoProcessSingleTask.bpmn) instead of `demoProcess.bpmn`

## WorkflowInstanceWithResultCreator.java

[Source on github](https://github.com/zeebe-io/zeebe/tree/{{commit}}/samples/src/main/java/io/zeebe/example/workflow/WorkflowInstanceWithResultCreator.java)

```java
{{#include ../../../../samples/src/main/java/io/zeebe/example/workflow/WorkflowInstanceWithResultCreator.java}}
```
