# Parallel Gateway (AND)

![workflow](/bpmn-workflows/parallel-gateway-example.png)

A parallel gateway is only activated, when a token has arrived on each of its incoming sequence flows. Once activated, all of the outgoing sequence flows are taken. So in the case of multiple outgoing sequence flows the branches are executed concurrently. Execution progresses independently until a synchronizing element is reached, for example another merging parallel gateway.

## Payload Merge

When the gateway is activated, then the payloads of each incoming path are merged into one document. A copy of this document is then propagated on each of the outgoing sequence flows. If fine-grained control over the merging process is required, then a *merging mapping* can be added to the gateway's incoming sequence flows. See the section on [merging mappings](/bpmn-workflows/data-flow.html#merging-mappings) for details.

XML representation:

```xml
<bpmn:sequenceFlow id="flow1">
  <extensionElements>
    <zeebe:payloadMappings>
      <zeebe:mapping source="$.total" target="$.sum" type="PUT" />
      <zeebe:mapping source="$.flightPrice" target="$.prices" type="COLLECT" />
    </zeebe:payloadMappings>
  </extensionElements>
</sequenceFlow>
```

## Payload Split

On activation, the merged payload is propagated on each of the outgoing sequence flows.
