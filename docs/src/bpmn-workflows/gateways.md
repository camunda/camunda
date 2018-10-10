# Gateways

Currently supported elements:

![workflow](/bpmn-workflows/exclusive-gateway.png)
![workflow](/bpmn-workflows/parallel-gateway.png)

## Exclusive Gateway (XOR)

![workflow](/bpmn-workflows/xor-gateway.png)

An exclusive gateway chooses one of its outgoing sequence flows for continuation.
Each sequence flow has a condition that is evaluated in the context of the current workflow instance payload.
The workflow instance takes the first sequence flow which condition is fulfilled.

If no condition is fulfilled, then it takes the default flow which has no condition.
In case the gateway has no default flow (not recommended), the execution stops and an incident is created.

Read more about conditions in the [JSON Conditions reference](reference/json-conditions.html).

XML representation:

```xml
<bpmn:exclusiveGateway id="exclusiveGateway" default="else" />

<bpmn:sequenceFlow id="priceGreaterThan100" name="$.totalPrice &#62; 100" sourceRef="exclusiveGateway" targetRef="shipParcelWithInsurance">
  <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">
    <![CDATA[ $.totalPrice > 100 ]]>
  </bpmn:conditionExpression>
</bpmn:sequenceFlow>

<bpmn:sequenceFlow id="else" name="else" sourceRef="exclusiveGateway" targetRef="shipParcel" />
```

BPMN Modeler: [Click Here](/bpmn-modeler/gateways.html#create-an-exclusive-gateway)

## Parallel Gateway (AND)

![workflow](/bpmn-workflows/parallel-gateway-example.png)

A parallel gateway is only activated, when a token has arrived on each of its incoming sequence flows. Once activated, all of the outgoing sequence flows are taken. So in the case of multiple outgoing sequence flows the branches are executed concurrently. Execution progresses independently until a synchronizing element is reached, for example another merging parallel gateway.

### Payload Merge

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

### Payload Split

On activation, the merged payload is propagated on each of the outgoing sequence flows.
