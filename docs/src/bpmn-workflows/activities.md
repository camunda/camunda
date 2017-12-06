# Activities

Currently, Zeebe supports service tasks.

## Service Tasks

![workflow](/bpmn-workflows/order-process.png)

A service task represents a work item in the workflow with a specific type.
When the workflow instance arrives a service task then it creates a corresponding task.

A worker can subscribe to these tasks and complete them when the work is done.
When a task is completed, the corresponding service task is left and the workflow instance continues.

Read more about [tasks](basics/task-workers.html).

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
It specifies the type of the task which is used by the workers to subscribe to.

Optionally, a task definition can specify the amount of times the task is executed if a failure occurs. (default = 3)

```
<zeebe:taskDefinition type="payment-service" retries="5" />
```

### Task Headers

A service task can have one or more task headers.
It is a kind of metadata of the task which can used as configuration or parameters.

```
<zeebe:taskHeaders>
  <zeebe:header key="method" value="VISA" />
</zeebe:taskHeaders>
```
