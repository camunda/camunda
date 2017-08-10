# Event Consumers

As Zeebe processes Tasks and Workflows, it generates a Stream of Events:

```
DEPLOYMENT: {"state":"CREATE_DEPLOYMENT", ...}
DEPLOYMENT: {"state":"DEPLOYMENT_CREATED", ...}
WORKFLOW: {"state":"CREATED","bpmnProcessId":"order-orchestration","version":1,"deploymentKey":4294967392}
WORKFLOW_INSTANCE: {"state":"CREATE_WORKFLOW_INSTANCE","bpmnProcessId":"order-orchestration","version":-1,"workflowKey":-1,"workflowInstanceKey":-1,"payload":"gahvcmRlci1pZKUxMjM0NQ=="}
WORKFLOW_INSTANCE: {"state":"WORKFLOW_INSTANCE_CREATED","bpmnProcessId":"order-orchestration","version":1,"workflowKey":4294977240,"workflowInstanceKey":4294982208,"activityId":"","payload":"gahvcmRlci1pZKUxMjM0NQ=="}
WORKFLOW_INSTANCE: {"state":"START_EVENT_OCCURRED","bpmnProcessId":"order-orchestration","version":1,"workflowKey":4294977240,"workflowInstanceKey":4294982208,"activityId":"StartEvent_1","payload":"gahvcmRlci1pZKUxMjM0NQ=="}
WORKFLOW_INSTANCE: {"state":"SEQUENCE_FLOW_TAKEN","bpmnProcessId":"order-orchestration","version":1,"workflowKey":4294977240,"workflowInstanceKey":4294982208,"activityId":"SequenceFlow_0bd5wty","payload":"gahvcmRlci1pZKUxMjM0NQ=="}
WORKFLOW_INSTANCE: {"state":"ACTIVITY_READY","bpmnProcessId":"order-orchestration","version":1,"workflowKey":4294977240,"workflowInstanceKey":4294982208,"activityId":"Task_19mjg6u","payload":"gahvcmRlci1pZKUxMjM0NQ=="}
WORKFLOW_INSTANCE: {"state":"ACTIVITY_ACTIVATED","bpmnProcessId":"order-orchestration","version":1,"workflowKey":4294977240,"workflowInstanceKey":4294982208,"activityId":"Task_19mjg6u","payload":"gahvcmRlci1pZKUxMjM0NQ=="}
TASK: {"state":"CREATE","lockTime":-9223372036854775808,"lockOwner":"","retries":3,"type":"process-payment","headers":{"bpmnProcessId":"order-orchestration","workflowDefinitionVersion":1,"workflowKey":4294977240,"workflowInstanceKey":4294982208,"activityId":"Task_19mjg6u","activityInstanceKey":4294983280},"customHeaders":{},"payload":"gahvcmRlci1pZKUxMjM0NQ=="}
TASK: {"state":"CREATED","lockTime":-9223372036854775808,"lockOwner":"","retries":3,"type":"process-payment","headers":{"bpmnProcessId":"order-orchestration","workflowDefinitionVersion":1,"workflowKey":4294977240,"workflowInstanceKey":4294982208,"activityId":"Task_19mjg6u","activityInstanceKey":4294983280},"customHeaders":{},"payload":"gahvcmRlci1pZKUxMjM0NQ=="}
```

In Zeebe you can subscribe to this stream at any point to gain visibility into the processing. Since the stream is stored on the broker, you can process the events after the fact.

An important concept is the _position_ of a subscription. When opening a subscription to a topic, a consumer can choose to open the subscription at the head or tail of the topic or anywhere in between. Opening the subscription at the head allows you to process all past events up to the present and then the future. Opening the subscription at the tail will deliver all future events from the point in time the subscription was opened. The broker manages the positions of the subscriptions allowing clients to disconnect and go offline. When the client reconnects, it will continue processing at the position it has last acknowledged.
