# None Events

None events are unspecified events, also called ‘blank’ events.

## None Start Events

![workflow](/bpmn-workflows/none-start-event.png)

A workflow must have exactly one none start event. The event is triggered when the workflow is started via API and in consequence a token spawns at the event.

XML representation:

```
<bpmn:startEvent id="order-placed" name="Order Placed" />
```

## None End Events

![workflow](/bpmn-workflows/none-end-event.png)

A workflow can have one or more none end events.
When a token arrives at an end event, then the it is consumed.
If it is the last token within a *scope* (top-level workflow or sub process), then the scope instance ends.

XML representation:

```
<bpmn:endEvent id="order-delivered" name="Order Delivered" />
```

Note that an activity without outgoing sequence flow has the same semantics as a none end event.
After the task is completed, the token is consumed and the workflow instance may end.

### Payload Merge

When all execution paths within a scope have ended, then the respective payloads are merged into one document that is used for scope completion. If fine-grained control over the merging process is required, then a *merging mapping* can be used. See the section on [merging mappings](/bpmn-workflows/data-flow.html#merging-mappings) for details.

XML representation:

```xml
<bpmn:endEvent id="order-delivered" name="Order Delivered">
  <extensionElements>
    <zeebe:payloadMappings>
      <zeebe:mapping source="$.paymentMethod" target="$.paymentMethod" />
      <zeebe:mapping source="$.price" target="$.prices" type="COLLECT" />
    </zeebe:payloadMappings>
  </extensionElements>
</serviceTask>
```
