# Exclusive Gateway

An exclusive gateway (aka XOR-gateway) allows to make a decision based on data (i.e. on workflow instance variables).

![workflow](/bpmn-workflows/exclusive-gateways/exclusive-gateway.png)
 
If an exclusive gateway has multiple outgoing sequence flows then all sequence flows, except one, **must** have a `conditionExpression` to define when the flow is taken. The gateway can have one sequence flow without `conditionExpression` which must be defined as the default flow.

When an exclusive gateway is entered then the `conditionExpression`s are evaluated. The workflow instance takes the first sequence flow that condition is fulfilled.

If no condition is fulfilled then it takes the **default flow** of the gateway. In case the gateway has no default flow, an incident is created.

An exclusive gateway can also be used to **join** multiple incoming flows to one, in order to improve the readability of the BPMN. A joining gateway has a pass-through semantic. It doesn't merge the incoming concurrent flows like a parallel gateway.   

## Conditions

A `conditionExpression` defines when a flow is taken. The expression can access the workflow instance variables and compare them with literals or other variables. Comparisons can be combined with logical operators.

For example:

```js
totalPrice > 100

order.customer == "Paul"

orderCount > 15 || totalPrice > 50
```

## Additional Resources

<details>
  <summary>XML representation</summary>
  <p>An exclusive gateway with two outgoing sequence flows:

```xml
<bpmn:exclusiveGateway id="exclusiveGateway" default="else" />

<bpmn:sequenceFlow id="priceGreaterThan100" name="totalPrice &#62; 100" 
  sourceRef="exclusiveGateway" targetRef="shipParcelWithInsurance">
  <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">
    <![CDATA[ totalPrice > 100 ]]>
  </bpmn:conditionExpression>
</bpmn:sequenceFlow>

<bpmn:sequenceFlow id="else" name="else" 
  sourceRef="exclusiveGateway" targetRef="shipParcel" />
```

  </p>
</details>

<details>
  <summary>Using the BPMN modeler</summary>
  <p>Adding an exclusive gateway with two outgoing sequence flows:

![exclusive-gateway](/bpmn-workflows/exclusive-gateways/exclusive-gateway.gif) 
  </p>
</details>

<details>
  <summary>Workflow Lifecycle</summary>
  <p>Workflow instance records of an exclusive gateway: 

<table>
    <tr>
        <th>Intent</th>
        <th>Element Id</th>
        <th>Element Type</th>
    </tr>    
    <tr>
        <td>ELEMENT_ACTIVATING</td>
        <td>shipping-gateway</td>
        <td>EXCLUSIVE_GATEWAY</td>
    <tr>
    <tr>
        <td>ELEMENT_ACTIVATED</td>
        <td>shipping-gateway</td>
        <td>EXCLUSIVE_GATEWAY</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETING</td>
        <td>shipping-gateway</td>
        <td>EXCLUSIVE_GATEWAY</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETED</td>
        <td>shipping-gateway</td>
        <td>EXCLUSIVE_GATEWAY</td>
    <tr>
    <tr>
        <td>SEQUENCE_FLOW_TAKEN</td>
        <td>priceGreaterThan100</td>
        <td>SEQUENCE_FLOW</td>
    <tr>
</table>

  </p>
</details>

References:
* [Conditions](/reference/conditions.html)
* [Incidents](/reference/incidents.html)
