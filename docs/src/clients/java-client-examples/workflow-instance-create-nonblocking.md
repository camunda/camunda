# Create Workflow Instances Non-Blocking

## Prerequisites

1. Running Zeebe broker with endpoint `localhost:26500` (default)
1. Run the [Deploy a Workflow example](/clients/java-client-examples/workflow-deploy.html)

## NonBlockingWorkflowInstanceCreator.java

[Source on github](https://github.com/zeebe-io/zeebe/tree/{{commit}}/samples/src/main/java/io/zeebe/example/workflow/NonBlockingWorkflowInstanceCreator.java)

```java
{{#include ../../../../samples/src/main/java/io/zeebe/example/workflow/NonBlockingWorkflowInstanceCreator.java}}
```
