# Record Consumers

As Zeebe processes jobs and workflows, it generates an ordered stream of records:

![record-stream](/basics/record-consumers-stream.png)

Each record has attributes that relate it to a workflow and a particular workflow instance. For example, the record for a started workflow instance looks as follows:

```
Record Type: EVENT
Value Type: WORKFLOW_INSTANCE
Intent: CREATED
Body:
{
	"bpmnProcessId": "process",
	"version": 1,
	"workflowKey": 4294970288,
	"workflowInstanceKey": 4294969008,
	"activityId": "",
	"payload": "gaNmb297"
}
```

Zeebe clients can subscribe to this stream at any point to gain visibility into the processing, called a *topic subscription*. Since the broker persists the record stream, you can process records at any time after occurrence.

For example, you can build applications that:

* Count the number of instances per workflow
* Send an alert when a job has failed
* Keep track of KPIs

An important concept is the _position_ of a subscription. When opening a subscription, a consumer can choose to open the subscription at the start or the current end of the stream or anywhere in between. The broker keeps track of a subscription's position, allowing clients to disconnect and go offline. When the client reconnects, it continues processing at the position it has last acknowledged.
