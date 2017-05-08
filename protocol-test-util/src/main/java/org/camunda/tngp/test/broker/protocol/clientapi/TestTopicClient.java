/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.test.broker.protocol.clientapi;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.test.util.collection.MapBuilder;

import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class TestTopicClient
{

    public static final String PROP_EVENT = "eventType";

    // workflow related properties

    public static final String PROP_WORKFLOW_BPMN_PROCESS_ID = "bpmnProcessId";
    public static final String PROP_WORKFLOW_BPMN_XML = "bpmnXml";
    public static final String PROP_WORKFLOW_VERSION = "version";
    private static final String PROP_WORKFLOW_PAYLOAD = "payload";

    private final ClientApiRule apiRule;
    private final String topicName;
    private final int partitionId;

    private boolean isTopicSubscriptionOpen = false;

    public TestTopicClient(final ClientApiRule apiRule, final String topicName, final int partitionId)
    {
        this.apiRule = apiRule;
        this.topicName = topicName;
        this.partitionId = partitionId;
    }

    public long deploy(final BpmnModelInstance modelInstance)
    {
        final ExecuteCommandResponse response = apiRule.createCmdRequest()
                .topicName(topicName)
                .partitionId(partitionId)
                .eventType(EventType.DEPLOYMENT_EVENT)
                .command()
                    .put(PROP_EVENT, "CREATE_DEPLOYMENT")
                    .put(PROP_WORKFLOW_BPMN_XML, Bpmn.convertToString(modelInstance))
                .done()
                .sendAndAwait();

        assertThat(response.getEvent().get(PROP_EVENT)).isEqualTo("DEPLOYMENT_CREATED");

        return response.key();
    }

    public long createWorkflowInstance(String bpmnProcessId)
    {
        final ExecuteCommandResponse response = sendCreateWorkflowInstanceRequest(bpmnProcessId);

        assertThat(response.getEvent().get(PROP_EVENT)).isEqualTo("WORKFLOW_INSTANCE_CREATED");

        return response.key();
    }

    public long createWorkflowInstance(String bpmnProcessId, byte[] payload)
    {
        final ExecuteCommandResponse response = sendCreateWorkflowInstanceRequest(bpmnProcessId, payload);

        assertThat(response.getEvent().get(PROP_EVENT)).isEqualTo("WORKFLOW_INSTANCE_CREATED");

        return response.key();
    }

    public ExecuteCommandResponse sendCreateWorkflowInstanceRequest(String bpmnProcessId)
    {
        return apiRule.createCmdRequest()
                .topicName(topicName)
                .partitionId(partitionId)
                .eventTypeWorkflow()
                .command()
                    .put(PROP_EVENT, "CREATE_WORKFLOW_INSTANCE")
                    .put(PROP_WORKFLOW_BPMN_PROCESS_ID, bpmnProcessId)
                .done()
                .sendAndAwait();
    }

    public ExecuteCommandResponse sendCreateWorkflowInstanceRequest(String bpmnProcessId, byte[] payload)
    {
        return apiRule.createCmdRequest()
            .topicName(topicName)
            .partitionId(partitionId)
            .eventTypeWorkflow()
            .command()
            .put(PROP_EVENT, "CREATE_WORKFLOW_INSTANCE")
            .put(PROP_WORKFLOW_BPMN_PROCESS_ID, bpmnProcessId)
            .put(PROP_WORKFLOW_PAYLOAD, payload)
            .done()
            .sendAndAwait();
    }

    public ExecuteCommandResponse sendCreateWorkflowInstanceRequest(String bpmnProcessId, int version)
    {
        return apiRule.createCmdRequest()
            .topicName(topicName)
            .partitionId(partitionId)
            .eventTypeWorkflow()
            .command()
            .put(PROP_EVENT, "CREATE_WORKFLOW_INSTANCE")
            .put(PROP_WORKFLOW_BPMN_PROCESS_ID, bpmnProcessId)
            .put(PROP_WORKFLOW_VERSION, version)
            .done()
            .sendAndAwait();
    }

    public void completeTaskOfType(String taskType)
    {
        completeTaskOfType(taskType, null);
    }

    public void completeTaskOfType(String taskType, byte[] payload)
    {
        apiRule.openTaskSubscription(topicName, partitionId, taskType).await();

        final SubscribedEvent taskEvent = apiRule
            .subscribedEvents()
            .filter(taskEvents("LOCKED"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected task locked event but not found."));

        final MapBuilder<ExecuteCommandRequestBuilder> mapBuilder = apiRule.createCmdRequest()
            .topicName(topicName)
            .partitionId(partitionId)
            .key(taskEvent.key())
            .eventTypeTask()
            .command()
            .put(PROP_EVENT, "COMPLETE")
            .put("type", taskType)
            .put("lockOwner", taskEvent.event().get("lockOwner"))
            .put("headers", taskEvent.event().get("headers"));

        if (payload != null)
        {
            mapBuilder.put("payload", payload);
        }

        final ExecuteCommandResponse response = mapBuilder.done().sendAndAwait();

        assertThat(response.getEvent().get(PROP_EVENT)).isEqualTo("COMPLETED");
    }

    /**
     * @return an infinite stream of received subscribed events; make sure to use short-circuiting operations
     *   to reduce it to a finite stream
     */
    public Stream<SubscribedEvent> receiveEvents(Predicate<SubscribedEvent> filter)
    {
        ensureOpenTopicSubscription();

        return apiRule
                .moveMessageStreamToHead()
                .subscribedEvents()
                .filter(filter);
    }

    public SubscribedEvent receiveSingleEvent(Predicate<SubscribedEvent> filter)
    {
        return receiveEvents(filter)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no event received"));
    }

    private void ensureOpenTopicSubscription()
    {
        if (!isTopicSubscriptionOpen)
        {
            final ExecuteCommandResponse response = apiRule.openTopicSubscription(topicName, partitionId, "test", 0).await();
            assertThat(response.key()).isGreaterThanOrEqualTo(0);

            isTopicSubscriptionOpen = true;
        }
    }

    public static Predicate<SubscribedEvent> eventType(String eventType)
    {
        return e -> e.event().get(PROP_EVENT).equals(eventType);
    }

    public static Predicate<SubscribedEvent> workflowInstanceEvents()
    {
        return e -> e.eventType() == EventType.WORKFLOW_EVENT;
    }

    public static Predicate<SubscribedEvent> workflowInstanceEvents(String eventType)
    {
        return workflowInstanceEvents().and(eventType(eventType));
    }

    public static Predicate<SubscribedEvent> taskEvents()
    {
        return e -> e.eventType() == EventType.TASK_EVENT;
    }

    public static Predicate<SubscribedEvent> taskEvents(String eventType)
    {
        return taskEvents().and(eventType(eventType));
    }

    public static Predicate<SubscribedEvent> incidentEvents()
    {
        return e -> e.eventType() == EventType.INCIDENT_EVENT;
    }

    public static Predicate<SubscribedEvent> incidentEvents(String eventType)
    {
        return incidentEvents().and(eventType(eventType));
    }
}
