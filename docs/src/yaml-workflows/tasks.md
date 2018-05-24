# Tasks

A workflow can contain multiple tasks which represents the work that should be done.

```yaml
name: order-process

tasks:
    - id: collect-money
      type: payment-service

    - id: fetch-items
      type: inventory-service
      retries: 5

    - id: ship-parcel
      type: shipment-service
      headers:
            method: "express"
            withInsurance: false
```

Each task has the following properties:

* `id` (*required*): the unique identifier of the task.
* `type` (*required*): the name which job workers can subscribe to.
* `retries`: the amount of times the job is retried if a failure occurs. (default = 3)
* `headers`: a list of additional metadata or configuration which can be read by the worker.

Read more about the concept of [jobs](basics/task-workers.html).
