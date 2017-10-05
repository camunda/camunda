# Events

Currently, Zeebe only supports two types of events:

* None Start Events
* None End Events

![workflow](/bpmn-workflows/order-process.png)

## Start Events

A workflow must have one none start event.
When a workflow instance is created then a new token starts at this event.

XML representation:

```
<bpmn:startEvent id="order-placed" name="Order Placed" />
```

## End Events

A workflow can have one or more none end event.
When a workflow instance arrives an end event then the current token is consumed.
If it is the last token then the workflow instance ends.

XML representation:

```
<bpmn:endEvent id="order-delivered" name="Order Delivered" />
```

Note that an activity without outgoing sequence flow has the same semantics as an end event.
After the task is completed, the current token is consumed and the workflow instance might end.
