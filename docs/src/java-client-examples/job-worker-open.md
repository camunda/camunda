# Open a Job Worker

## Prerequisites

1. Running Zeebe broker with endpoint `localhost:51015` (default)
1. Run the [Deploy a Workflow example](java-client-examples/workflow-deploy.html)
1. Run the [Create a Workflow Instance example](java-client-examples/workflow-instance-create.html) a couple of times

## JobWorkerCreator.java

[Source on github](https://github.com/zeebe-io/zeebe/tree/{{commit}}/samples/src/main/java/io/zeebe/example/job/JobWorkerCreator.java)

```java
{{#include ../../../samples/src/main/java/io/zeebe/example/job/JobWorkerCreator.java}}
```
