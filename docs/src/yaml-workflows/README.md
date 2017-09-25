# YAML Workflows

Zeebe provides a YAML format to define plain workflows. It's an alternative to BPMN 2.0, but doesn't provide the same flexibility or a visual representation. Internally, a YAML workflow is transformed into a BPMN workflow.

The targeted user group has a technical background and has no need for BPMN 2.0.

```yaml
name: order-process

tasks:
    - id: collect-money
      type: payment-service

    - id: fetch-items
      type: inventory-service

    - id: ship-parcel
      type: shipment-service
```

Read more about:

* [Tasks](yaml-workflows/tasks.html)
* [Data Flow](yaml-workflows/data-flow.html)
* [Control Flow](yaml-workflows/control-flow.html)
