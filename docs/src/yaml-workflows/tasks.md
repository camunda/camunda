# Tasks

A workflow can contain a sequence of tasks. This sequence defines the order how the tasks are executed in the workflow.

```yaml
name: workflow-name

tasks:    
    - id: task1
      type: foo
      retries: 3

    - id: task2
      type: bar
```

A task has the following properties:

* id: (required) the unique identifier of the task.
* type: (required) the name which is used by the task workers to register for.
* retries: the number how often the task is executed if a failure occurs.
* headers: a list of additional metadata or configuration which can be read by a task worker.
* inputs: a list of mappings which defines how the workflow instance payload is mapped into the task.
* outputs: a list of mappings which defines how the task payload is mapped into the workflow instance.

## Task Headers

A task can have one or more headers.

```yaml
tasks:    
    - id: task1
      type: foo
      headers:
            key1: bar
            key2: 21
            key3: true
```

Each header is a key-value pair of string to string / number / boolean.

## Input and Output Mappings

A task can have one or more input and output mappings.

```yaml
tasks:    
    - id: task1
      type: foo
      inputs:
            - source: $.a
              target: $.b
      outputs:
            - source: $.c
              target: $.d
```

Each mapping has a source and a target property in form of a JSON path expression.
The source property defines how the value is extracted and the target property how the value is inserted in the payload.

More information about the payload mapping can be found in the [data flow section](bpmn-workflows/data-flow.html).
