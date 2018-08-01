# Request all Workflows

## Prerequisites

1. Running Zeebe broker with endpoint `localhost:26500` (default)
1. Make sure a couple of workflows are deployed, e.g. run the [Deploy a Workflow example](java-client-examples/workflow-deploy.html) multiple times to create multiple workflow versions.

## DeploymentViewer.java

[Source on github](https://github.com/zeebe-io/zeebe/tree/{{commit}}/samples/src/main/java/io/zeebe/example/workflow/DeploymentViewer.java)

```java
{{#include ../../../samples/src/main/java/io/zeebe/example/workflow/DeploymentViewer.java}}
```
