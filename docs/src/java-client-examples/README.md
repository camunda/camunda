# Example Code using the Zeebe Java Client

These examples are accessible in the [zeebe-io github repository](https://github.com/zeebe-io/zeebe/) at commit `{{commit}}`. [Link to browse code on github](https://github.com/zeebe-io/zeebe/tree/{{commit}}/samples).

Instructions to access code locally:

```
git clone https://github.com/zeebe-io/zeebe.git
git checkout {{commit}}
cd zeebe/samples
```

Import the Maven project in the `samples` directory into your IDE to start hacking.

## Workflow

* [Deploy a Workflow](java-client-examples/workflow-deploy.html)
* [Create a Workflow Instance](java-client-examples/workflow-instance-create.html)
* [Create Workflow Instances Non-Blocking](java-client-examples/workflow-instance-create-nonblocking.html)
* [Request all Workflows](java-client-examples/workflow-deployment-request.html)

## Job

* [Open a Job Worker](java-client-examples/job-worker-open.html)

## Data

* [Handle variables as POJO](java-client-examples/data-pojo.html)

## Cluster

* [Request Cluster Topology](java-client-examples/cluster-topology-request.html)
