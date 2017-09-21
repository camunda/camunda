# Control Flow

A workflow can contain multiple tasks.
The control flow defines when these tasks are executed (e.g. one after another, parallel, exclusively etc.).

## Sequences

In a sequence, a task is created after the previous one is completed.
The order of the sequence is defined implicitly by the order in the YAML file.

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

For example, the order-process starts with _collect-money_, followed by _fetch-items_ and ends with _ship-parcel_.

## Conditional Flows

Some workflows do not always execute the same tasks but need to pick and choose different tasks based on workflow instance payload.

In this cases, a task can define the next task explicitly by conditional flows.
Each flow has a condition and a reference to the following task.
The workflow instance takes the first flow which condition is fulfilled.

If no condition is fulfilled then it takes the default flow.
In case that no default flow exists (not recommended), the execution stops and an incident is created.

Unlike sequences, all other tasks must also define the following task explicitly to avoid ambiguity.
If a task defines no following task then the workflow instance ends at this point.

```yaml
name: order-process

tasks:
    - id: collect-money
      type: payment-service
      next: fetch-items

    - id: fetch-items
      type: inventory-service
      flows:
          - condition: $.totalPrice > 100
            next: ship-parcel-with-insurance

      defaultFlow: ship-parcel      

    - id: ship-parcel-with-insurance
      type: shipment-service-premium

    - id: ship-parcel
      type: shipment-service-normal
```

For example, the order-process starts with _collect-money_ and followed by _fetch-items_.
If the _totalPrice_ is greater than 100 then it continues with _ship-parcel-with-insurance_.
Otherwise, with _ship-parcel_.
In both cases, the workflow instance ends because no following task is defined.

Read more about [conditions](basics/json.html#conditions).
