# Tasks

A workflow can contain multiple tasks, where each represents a step in the workflow.

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
* `type` (*required*): the name to which job workers can subscribe.
* `retries`: the amount of times the job is retried in case of failure. (default = 3)
* `headers`: a list of metadata in the form of key-value pairs that can be accessed by a worker.

When Zeebe executes a task, it creates a job that is handed to a job worker. The worker can perform the business logic and complete the job eventually to trigger continuation in the workflow.

Related resources:

* [Introduction to Jobs and Job Workers](/basics/job-workers.html)

