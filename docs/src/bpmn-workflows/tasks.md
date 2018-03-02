# Tasks

Currently supported elements:

![workflow](/bpmn-workflows/service-task.png)

## Service Tasks

![workflow](/bpmn-workflows/order-process.png)

A service task represents a work item in the workflow with a specific type.
When the workflow instance arrives a service task then it creates a corresponding task instance. The token flow stops at this point.

A worker can subscribe to these instances and complete them when the work is done.
When a task is completed, the token flow continues.

Read more about [task handling](basics/task-workers.html).

XML representation:

```
<bpmn:serviceTask id="collect-money" name="Collect Money">
  <bpmn:extensionElements>
    <zeebe:taskDefinition type="payment-service" />
    <zeebe:taskHeaders>
      <zeebe:header key="method" value="VISA" />
    </zeebe:taskHeaders>
  </bpmn:extensionElements>
</bpmn:serviceTask>
```

### Task Definition

Each service task must have a task definition.
It specifies the type of the task which workers can subscribe to.

Optionally, a task definition can specify the amount of times the task is retried when a worker signals task failure. (default = 3)

```
<zeebe:taskDefinition type="payment-service" retries="5" />
```

### Task Headers

A service task can define an arbitrary number of task headers.
Task headers are metadata that are handed to task workers along with the task. They can be used as configuration parameters for the task worker.

```
<zeebe:taskHeaders>
  <zeebe:header key="method" value="VISA" />
</zeebe:taskHeaders>
```
