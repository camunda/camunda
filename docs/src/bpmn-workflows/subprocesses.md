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

## Variable Mappings

Input mappings can be used to create new variables in the scope of the sub process. These variables are only visible within the sub process.

```xml
<bpmn:subProcess id="shipping" name="Shipping">
  <bpmn:extensionElements>
    <zeebe:ioMapping>
      <zeebe:input source="order.id" target="trackingId"/>
     </zeebe:ioMapping>
  </bpmn:extensionElements>
</bpmn:subProcess>
```

## Additional Resources

* [Variable Mappings](reference/variables.html#inputoutput-variable-mappings)
