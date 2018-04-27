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

BPMN Modeler: [Click Here](/bpmn-modeler/tasks.html#create-a-service-task)

### Task Definition

Each service task must have a task definition.
It specifies the type of the task which workers can subscribe to.

Optionally, a task definition can specify the amount of times the task is retried when a worker signals task failure. (default = 3)

```
<zeebe:taskDefinition type="payment-service" retries="5" />
```

BPMN Modeler: [Click Here](/bpmn-modeler/tasks.html#configure-task-type)

### Task Headers

A service task can define an arbitrary number of task headers.
Task headers are metadata that are handed to task workers along with the task. They can be used as configuration parameters for the task worker.

```
<zeebe:taskHeaders>
  <zeebe:header key="method" value="VISA" />
</zeebe:taskHeaders>
```

BPMN Modeler: [Click Here](/bpmn-modeler/tasks.html#add-task-header)

### Payload Mapping

In order to map workflow instance payload to a format that is accepted by the task worker, payload mappings can be configured. We distinguish between *input* and *output* mappings. Input mappings are used to extract data from the workflow instance payload and generate the task payload. Output mappings are used to merge the task result with the workflow instance payload on task completion.

Payload mappings can be defined as pairs of JSON-Path expressions. Each mapping has a *source* and a *target* expression. The source expressions describes the path in the source document from which to copy data. The target expression describes the path in the new document that the data should be copied to. When multiple mappings are defined, they are applied in the order of their appearance. For details and examples, see the references on [JSONPath](reference/json-path.html) and [JSON Payload Mapping](reference/json-payload-mapping.html).

Example:

![payload](/bpmn-workflows/payload3.png)

XML representation:

```xml
<serviceTask id="collectMoney">
    <extensionElements>
      <zeebe:ioMapping>
        <zeebe:input source="$.price" target="$.total"/>
        <zeebe:output source="$.paymentMethod" target="$.paymentMethod"/>
       </zeebe:ioMapping>
    </extensionElements>
</serviceTask>
```

BPMN Modeler: [Click Here](/bpmn-modeler/tasks.html#add-inputoutput-mapping)
