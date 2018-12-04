# Data Flow

Every BPMN workflow instance has a JSON document associated with it, called the *workflow instance payload*. The payload carries contextual data of the workflow instance that is required by job workers to do their work. It can be provided when a workflow instance is created. Job workers can read and modify it.

![payload](/bpmn-workflows/payload1.png)

Payload is the link between workflow instance context and workers and therefore a form of coupling. We distinguish two cases:

1. **Tightly coupled job workers**: Job workers work with the exact JSON structure that the workflow instance payload has. This approach is applicable when workers are used only in a single workflow and the payload format can be agreed upon. It is convenient when building both, workflow and job workers, from scratch.
1. **Loosely coupled job workers**: Job workers work with a different JSON structure than the workflow instance payload. This is often the case when job workers are reused for many workflows or when workers are developed independently of the workflow.

Without additional configuration, Zeebe assumes *tightly* coupled workers. That means, on job execution the workflow instance payload is provided as is to the job worker:

![payload](/bpmn-workflows/payload2.png)

When the worker modifies the payload, the result is merged on top-level into the workflow instance payload. In order to use *loosely* coupled job workers, the workflow can be extended by *payload mappings*.

## Payload Mappings

We distinguish between *input* and *output* mappings. Mappings are used to adapt payload to the context of an activity.

Payload mappings are pairs of [JSONPath](http://goessner.net/articles/JsonPath/) expressions. Every mapping has a *source* and a *target* expression. The source expression describes the path in the source document from which to copy data. The target expression describes the path in the new document that the data should be copied to. When multiple mappings are defined, they are applied in the order of their appearance.

**Note**: Mappings are not a tool for performance optimization. While a smaller document can save network bandwidth when publishing the job, the broker has extra effort of applying the mappings during workflow execution.

### Input/Output Mappings

Before starting a workflow element, Zeebe applies input mappings to the payload and generates a new JSON document. Upon element completion, output mappings are applied to map the result back into the workflow instance payload.

If a mapping can't be applied then an incident is created.

Examples in BPMN:

* Service Task: Input and output mappings can be used to adapt the workflow instance payload to the job worker.
* Message Catch Event: Output mappings can be used to merge the message payload into the workflow instance payload.

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

When no output mapping is defined, the job payload is by default merged into the workflow instance payload.
This default output behavior is configurable via the `outputBehavior` attribute on the `<ioMapping>` tag.
It accepts three differents values:

 * **MERGE** merges the job payload into the workflow instance payload, if no output mapping is specified.  If output mappings are specified, then the payloads are merged according to those.
 *This is the default output behavior.*
 * **OVERWRITE** overwrites the workflow instance payload with the job payload. If output mappings are specified, then the content is extracted from the job payload, which then overwrites the workflow instance payload.
 * **NONE** indicates that the worker does not produce any output. Output mappings cannot be used in combination with this behavior. The Job payload is simply ignored on job completion and the workflow instance payload remains unchanged.

Example:

```xml
<serviceTask id="collectMoney">
    <extensionElements>
      <zeebe:ioMapping outputBehavior="overwrite">
        <zeebe:input source="$.price" target="$.total"/>
        <zeebe:output source="$.paymentMethod" target="$.paymentMethod"/>
       </zeebe:ioMapping>
    </extensionElements>
</serviceTask>
```

## Additional Resources

* [Input Mapping Examples](/reference/json-payload-mapping.html#input-mapping)
* [Output Mapping Examples](/reference/json-payload-mapping.html#output-mapping)
* [JSONPath Reference](/reference/json-path.html)
* [Incidents](/reference/incidents.html)


