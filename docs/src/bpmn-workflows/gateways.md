# Gateways

Currently, Zeebe supports the exclusive gateway.

## Exclusive Gateway (XOR)

![workflow](/bpmn-workflows/xor-gateway.png)

An exclusive gateway allows to choose between different sequence flows.
Each sequence flow has a condition based on the workflow instance payload.
The workflow instance takes the first sequence flow which condition is fulfilled.

If no condition is fulfilled, then it takes the default flow which has no condition.
In case the gateway has no default flow (not recommended), the execution stops and an incident is created.

XML representation:

```
<bpmn:exclusiveGateway id="exclusiveGateway" default="else" />

<bpmn:sequenceFlow id="priceGreaterThan100" name="$.totalPrice &#62; 100" sourceRef="exclusiveGateway" targetRef="shipParcelWithInsurance">
  <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">
    <![CDATA[ $.totalPrice > 100 ]]>
  </bpmn:conditionExpression>
</bpmn:sequenceFlow>

<bpmn:sequenceFlow id="else" name="else" sourceRef="exclusiveGateway" targetRef="shipParcel" />
```

Read more about [conditions](reference/json-conditions.html).
