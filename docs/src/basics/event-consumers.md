# Event Consumers

As Zeebe processes Tasks and Workflows, it generates an ordered stream of events:

![event-stream](/basics/event-consumers-stream.png)

Each event has attributes that relate it to a workflow and a particular workflow instance. For example, the event for starting a workflow instance looks as follows:

```
Type: WORKFLOW_INSTANCE
Body:
{
	"state": "WORKFLOW_INSTANCE_CREATED",
	"bpmnProcessId": "process",
	"version": 1,
	"workflowKey": 4294970288,
	"workflowInstanceKey": 4294969008,
	"activityId": "",
	"payload": "gaNmb297"
}
```

Zeebe clients can subscribe to this stream at any point to gain visibility into the processing. Since the broker persists the event stream, you can process the events at any time after occurrence.

For example, you can build applications that:

* Count the number of instances per workflow
* Send an alert when a task has failed
* Keep track of KPIs

An important concept is the _position_ of a subscription. When opening a subscription to a topic, a consumer can choose to open the subscription at the start or the current end of the stream or anywhere in between. The broker keeps track of a subscription's position, allowing clients to disconnect and go offline. When the client reconnects, it continues processing at the position it has last acknowledged.
