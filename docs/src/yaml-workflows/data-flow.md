# Data Flow

Zeebe carries a JSON document from task to task. This is called the *workflow instance payload*. For every task, we can define input and output mappings in order to transform the workflow instance payload JSON to a JSON document that the job worker can work with.

```yaml
name: order-process

tasks:
    - id: collect-money
      type: payment-service
      inputs:
          - source: $.totalPrice
            target: $.price
      outputs:
          - source: $.success
            target: $.paymentSuccess

    - id: fetch-items
      type: inventory-service

    - id: ship-parcel
      type: shipment-service
```

Every mapping element has a `source` and a `target` element which must be a JSON Path expression. `source` defines which data is extracted from the source payload and `target` defines how the value is inserted into the target payload.

Related resources:

* [More detailed introduction on data flow in the BPMN reference](bpmn-workflows/data-flow.html)
* [JSON Path Reference](reference/json-path.html)
* [Payload Mapping Reference](reference/json-payload-mapping.html)
