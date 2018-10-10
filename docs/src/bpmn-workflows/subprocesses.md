# Sub Processes

Currently supported elements:

![embedded-subprocess](/bpmn-workflows/embedded-sub-process.png)

## Embedded Sub Process

An embedded sub process can be used to group workflow elements. It must have a single none start event. When activated, execution starts at that start event. The sub process only completes when all contained paths of execution have ended.

XML representation:

```xml
<bpmn:subProcess id="shipping" name="Shipping">
  <bpmn:startEvent id="shipping-start" />
  ... more contained elements ...
</bpmn:subProcess>
```

### Payload Mapping

When a subprocess completes, the payloads of each executed end event are merged into the result document of the subprocess. See the [end events](/bpmn-workflows/events.html#end-events) section for how to configure the merge.
