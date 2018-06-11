# Request all Workflows

## Prerequisites

1. Running Zeebe broker with endpoint `localhost:51015` (default)
1. Make sure workflows are deployed, e.g. run the [Deploy a Workflow example](java-client-examples/workflow-deploy.html) multiple times

## DeploymentViewer.java

[Source on github](https://github.com/zeebe-io/zeebe/tree/{{commit}}/samples/src/main/java/io/zeebe/example/workflow/DeploymentViewer.java)

```java
{{#include ../../../samples/src/main/java/io/zeebe/example/workflow/DeploymentViewer.java}}
```
