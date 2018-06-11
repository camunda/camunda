# Deploy a Workflow

## Prerequisites

1. Running Zeebe broker with endpoint `localhost:51015` (default)

## WorkflowDeployer.java

[Source on github](https://github.com/zeebe-io/zeebe/tree/{{commit}}/samples/src/main/java/io/zeebe/example/workflow/WorkflowDeployer.java)

```java
{{#include ../../../samples/src/main/java/io/zeebe/example/workflow/WorkflowDeployer.java}}
```

## demoProcess.bpmn

[Source on github](https://github.com/zeebe-io/zeebe/tree/{{commit}}/samples/src/main/resources/demoProcess.bpmn)

Download the XML and save it in the Java classpath before running the example. Open the file with [Zeebe Modeler](bpmn-modeler/README.html) for a graphical representation.

```xml
{{#include ../../../samples/src/main/resources/demoProcess.bpmn}}
```