# Control Flow

A workflow can contain multiple tasks.
The control flow defines when these tasks are executed (e.g., sequentially, in parallel, exclusively, etc.).

## Sequences

In a sequence, a task is created after the previous one is completed.
The order of the sequence is implicitly defined by the order in the YAML file.

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

Some workflows do not always execute the same tasks but need to pick and choose different tasks, based on workflow instance payload.

In this case, a task can define the next task explicitly by conditional flows (switch-case construct).
Each flow has a condition and a reference to the following task.
The workflow instance takes the first flow of which the condition is fulfilled.

If no condition is fulfilled, then it takes the default flow.
In case no default flow exists (not recommended), the execution stops and an incident is created.

```yaml
name: order-process

tasks:
    - id: collect-money
      type: payment-service

    - id: fetch-items
      type: inventory-service
      switch:
          - case: $.totalPrice > 100
            goto: ship-parcel-with-insurance

          - default: ship-parcel

    - id: ship-parcel-with-insurance
      type: shipment-service-premium
      end: true

    - id: ship-parcel
      type: shipment-service
```

For example, the order-process starts with _collect-money_, followed by _fetch-items_.
If the _totalPrice_ is greater than 100, then it continues with _ship-parcel-with-insurance_.
Otherwise, with _ship-parcel_.
In both cases, the workflow instance ends because no following task is defined.

By default, a task after a conditional flow (e.g. _ship-parcel-with-insurance_) is followed by the next task in the sequence (_ship-parcel_).
If the workflow instance should end after the task then the task must have the property `end: true`.
In case that the workflow instance should continue with another task, the task must define the next task using the property `goto`.

Read more about [conditions](basics/json.html#conditions).
