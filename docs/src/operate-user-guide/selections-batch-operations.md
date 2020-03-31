# Selections & Batch Operations

In some cases, you’ll need to retry or cancel many workflow instances at once. Operate also supports this type of batch operation.

Imagine a case where many workflow instances have an incident incident caused by the same issue. At some point, the underlying problem will have been resolved (for example, maybe a microservice was down for an extended period of time then was brought back up). 

But even though the underlying problem was resolved, the affected workflow instances are stuck until they’re “retried”.

![operate-batch-retry](/operate-user-guide/img/Operate-Batch-Many-Instances-With-Incident.png)

Let's create a _selection_ in Operate. A selection is simply a set of workflow instances on which you can carry out a batch retry or batch cancellation. To create a selection, check the box next to the workflow instances you'd like to include, then click on the blue “Create Selection” button. 

![operate-batch-retry](/operate-user-guide/img/Operate-Batch-Create-Selection.png)

Your selection will appear in the right-side _Selections_ panel.

![operate-batch-retry](/operate-user-guide/img/Operate-Batch-Selection-Created.png)

You can retry or cancel the workflow instances in the selection immediately, or you can come back to the selection later–your selection will remain saved. And you can add more workflow instances to the selection after it was initially created via the blue “Add To Selection” button. 

![operate-batch-retry](/operate-user-guide/img/Operate-Batch-Selection-Saved.png)

When you’re ready to carry out an operation, you can simply return to the selection and retry or cancel the workflow instances as a batch. 

![operate-batch-retry](/operate-user-guide/img/Operate-Batch-Cancel-Or-Retry.png)

If the operation was successful, the state of the workflow instances will be updated to active and without incident.

![operate-batch-retry](/operate-user-guide/img/Operate-Batch-Retry-Successful.png)