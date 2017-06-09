# Java Client APIs

Entry points:

* `WorkflowTopicClient workflowsClient = client.workflowTopic(String topicName, int partitionId)`: Provides access to workflow-related operations on the given topic, such as process instantiation.
* `TaskTopicClient tasksClient = client.taskTopic(String topicName, int partitionId)`: Provides access to task-related operations on the given topic, such as task subscriptions.
* `TopicClient topicClient = client.topic(String topicName, int partitionId)`: Provides access to general-purpose operations on the given topic, such as topic subscriptions.

Then you can take it from there, the Javadoc should give you an idea what each method does.
