# Service Tasks

![workflow](/bpmn-workflows/order-process.png)

A service task represents a work item in the workflow with a specific type.
When the workflow instance arrives a service task then it creates a corresponding job. The token flow stops at this point.

A worker can subscribe to these jobs and complete them when the work is done.
When a job is completed, the token flow continues.

Read more about [job handling](basics/job-workers.html).

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

## Task Definition

Each service task must have a task definition.
It specifies the type of the job which workers can subscribe to.

Optionally, a task definition can specify the amount of times the job is retried when a worker signals failure (default = 3).

```
<zeebe:taskDefinition type="payment-service" retries="5" />
```

BPMN Modeler: [Click Here](/bpmn-modeler/tasks.html#configure-job-type)

## Task Headers

A service task can define an arbitrary number of task headers.
Task headers are metadata that are handed to workers along with the job. They can be used as configuration parameters for the worker.

```
<zeebe:taskHeaders>
  <zeebe:header key="method" value="VISA" />
</zeebe:taskHeaders>
```

BPMN Modeler: [Click Here](/bpmn-modeler/tasks.html#add-task-header)

## Payload Mapping

In order to map workflow instance payload to a format that is accepted by the job worker, payload input and output mappings can be configured. See the [Input/Output Mappings](/bpmn-workflows/data-flow.html#inputoutput-mappings) section for details on this concept.

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
