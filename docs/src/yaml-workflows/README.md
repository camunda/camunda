# YAML Workflows

In addition to BPMN, Zeebe provides a YAML format to define workflows. Creating a YAML workflow can be done with a regular text editor and does not require a graphical modelling tool. Internally, Zeebe transforms a deployed YAML file to BPMN.

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
