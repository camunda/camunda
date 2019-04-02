# Exclusive Gateway (XOR)

![workflow](/bpmn-workflows/xor-gateway.png)

An exclusive gateway chooses one of its outgoing sequence flows for continuation. Each sequence flow has a condition that is evaluated in the context of the current workflow instance. The workflow instance takes the first sequence flow which condition is fulfilled.

If no condition is fulfilled, then it takes the default flow which has no condition. In case the gateway has no default flow (not recommended), the execution stops and an incident is created.

## XML Representation

```xml
<bpmn:exclusiveGateway id="exclusiveGateway" default="else" />

<bpmn:sequenceFlow id="priceGreaterThan100" name="totalPrice &#62; 100" sourceRef="exclusiveGateway" targetRef="shipParcelWithInsurance">
  <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">
    <![CDATA[ totalPrice > 100 ]]>
  </bpmn:conditionExpression>
</bpmn:sequenceFlow>

<bpmn:sequenceFlow id="else" name="else" sourceRef="exclusiveGateway" targetRef="shipParcel" />
```

BPMN Modeler: [Click Here](/bpmn-modeler/gateways.html#exclusive-gateway)

## Additional Resources

* [Conditions](reference/conditions.html)
* [Incidents](/reference/incidents.html)
