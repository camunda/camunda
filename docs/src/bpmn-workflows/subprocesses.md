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

BPMN Modeler: [Click Here](/bpmn-modeler/subprocesses.html#embedded-sub-process)

## Variable Mappings

Input mappings can be used to create new variables in the scope of the sub process. These variables are only visible within the sub process.

By default, the variables of the sub process are not propagated (i.e. they are removed with the scope). This behavior can be customized by defining output mappings at the sub process. The output mappings are applied when the sub process is completed.

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
