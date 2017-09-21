# Data Flow

Data flow makes it possible to share data between different tasks in a workflow instance.

A task can have one or more input and output mappings to control the data which is mapped into and out of the task.

```yaml
name: order-process

tasks:
    - id: collect-money
      type: payment-service
      outputs:
          - source: $.totalPrice
            target: $.totalPrice

    - id: fetch-items
      type: inventory-service

    - id: ship-parcel
      type: shipment-service
      inputs:
          - source: $.shippingAddress
            target: $.address
```

Each mapping has a source and a target property in form of a JSON path expression.
The source property defines how the value is extracted and the target property how the value is inserted in the payload.

Read more about [input and output mappings](bpmn-workflows/data-flow.html#input-and-output-mapping).
