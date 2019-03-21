# Control Flow

*Control flow* is about the order in which tasks are executed. The YAML format provides tools to decide which task is executed when.

## Sequences

In a sequence, a task is executed after the previous one is completed.
By default, tasks are executed top-down as they are declared in the YAML file.

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

In the example above, the workflow starts with `collect-money`, followed by `fetch-items` and ends with `ship-parcel`.

We can use the `goto` and `end` attributes to define a different order:

```yaml
name: order-process

tasks:
    - id: collect-money
      type: payment-service
      goto: ship-parcel

    - id: fetch-items
      type: inventory-service
      end: true

    - id: ship-parcel
      type: shipment-service
      goto: fetch-items
```

In the above example, we have reversed the order of `fetch-items` and `ship-parcel`. Note that the `end` attribute is required so that workflow execution stops after `fetch-items`.

## Data-based Conditions

Some workflows do not always execute the same tasks but need to pick and choose different tasks, based on variables of the workflow instance.

We can use the `switch` attribute and conditions to decide on the next task.

```yaml
name: order-process

tasks:
    - id: collect-money
      type: payment-service

    - id: fetch-items
      type: inventory-service
      switch:
          - case: totalPrice > 100
            goto: ship-parcel-with-insurance

          - default: ship-parcel

    - id: ship-parcel-with-insurance
      type: shipment-service-premium
      end: true

    - id: ship-parcel
      type: shipment-service
```

In the above example, the order-process starts with `collect-money`, followed by `fetch-items`.
If the variable `totalPrice` is greater than 100, then it continues with `ship-parcel-with-insurance`. Otherwise, `ship-parcel` is chosen. In either case, the workflow instance ends after that.

In the `switch` element, there is one `case` element per alternative to choose from. If none of the conditions evaluates to `true`, then the `default` element is evaluated. While `default` is not required, it is best practice to include to avoid errors at workflow runtime. Should such an error occur (i.e. no case is fulfilled and there is no default), then workflow execution stops and an incident is raised.

## Additional Resources

* [Conditions](reference/conditions.html)
