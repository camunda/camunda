# Simple Workflows

Zeebe provides a YAML format to define simple workflows. It's an alternative to BPMN 2.0 but doesn't provide the same flexibility or a visual representation. Internally, a simple workflow is transformed into a BPMN workflow.

The targeting user group has a technical background and don't know, or has no need for BPMN 2.0.

A minimal example of a workflow with two tasks can look like this:

```yaml
name: workflow-name

tasks:    
    - id: task1
      type: foo

    - id: task2
      type: bar
```

Read more about:

* [Tasks](simple-workflows/tasks.html)
