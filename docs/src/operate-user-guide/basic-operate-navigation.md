# Getting Familiar With Operate

This section "Getting Familiar With Operate" and the next section “Incidents and Payloads” assumes that you’ve deployed a workflow to Zeebe and have created at least one workflow instance. 

If you’re not sure how to deploy workflows or create instances, we recommend going through the [Getting Started tutorial](/getting-started/). 

In the following sections, we’ll use the same `order-process.bpmn` workflow model from the Getting Started guide. 

## View A Deployed Workflow

In the “Instances by Workflow” panel in your dashboard, you should see a list of your deployed workflows and running instances. 

![operate-dash-with-workflows](/operate-user-guide/img/Operate-Dashboard-Deployed-Workflow.png)

When you click on the name of a deployed workflow in the “Instances by Workflow” panel, you’ll navigate to a view of that workflow model along with all running instances.

![operate-view-workflow](/operate-user-guide/img/Operate-View-Workflow.png)

From this “Running Instances” view, you have the ability to cancel a single running workflow instance. 

![operate-cancel-workflow-instance](/operate-user-guide/img/Operate-View-Workflow-Cancel.png)

## Inspect A Workflow Instance

Running workflow instances appear in the “Instances” section below the workflow model. To inspect a specific instance, you can click on the instance ID. 

![operate-inspect-instance](/operate-user-guide/img/Operate-Workflow-Instance-ID.png)

There, you’ll be able to see detail about the workflow instance, including the instance history and the variables attached to the instance. 

![operate-view-instance-detail](/operate-user-guide/img/Operate-View-Instance-Detail.png)


