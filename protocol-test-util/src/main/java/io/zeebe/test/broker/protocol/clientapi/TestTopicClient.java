/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.test.broker.protocol.clientapi;

import static io.zeebe.util.buffer.BufferUtil.bufferAsArray;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.test.util.collection.MapBuilder;
import org.agrona.DirectBuffer;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

public class TestTopicClient
{

    public static final String PROP_STATE = "state";

    // workflow related properties

    public static final String PROP_WORKFLOW_BPMN_PROCESS_ID = "bpmnProcessId";
    public static final String PROP_WORKFLOW_BPMN_XML = "bpmnXml";
    public static final String PROP_WORKFLOW_VERSION = "version";
    public static final String PROP_WORKFLOW_PAYLOAD = "payload";
    public static final String PROP_WORKFLOW_INSTANCE_KEY = "workflowInstanceKey";
    public static final String PROP_WORKFLOW_KEY = "workflowKey";

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
                    .put(PROP_STATE, "CREATE_DEPLOYMENT")
                    .put(PROP_WORKFLOW_BPMN_XML, Bpmn.convertToString(modelInstance).getBytes(UTF_8))
                .done()
                .sendAndAwait();

        assertThat(response.getEvent().get(PROP_STATE)).isEqualTo("DEPLOYMENT_CREATED");

        return response.key();
    }

    public ExecuteCommandResponse createWorkflowInstanceWithResponse(String bpmnProcessId)
    {
        return apiRule.createCmdRequest()
                      .topicName(topicName)
                      .partitionId(partitionId)
                      .eventTypeWorkflow()
                      .command()
                      .put(PROP_STATE, "CREATE_WORKFLOW_INSTANCE")
                      .put(PROP_WORKFLOW_BPMN_PROCESS_ID, bpmnProcessId)
                      .done()
                      .sendAndAwait();
    }

    public long createWorkflowInstance(String bpmnProcessId)
    {
        final ExecuteCommandResponse response = apiRule.createCmdRequest()
                .topicName(topicName)
                .partitionId(partitionId)
                .eventTypeWorkflow()
                .command()
                    .put(PROP_STATE, "CREATE_WORKFLOW_INSTANCE")
                    .put(PROP_WORKFLOW_BPMN_PROCESS_ID, bpmnProcessId)
                .done()
                .sendAndAwait();

        assertThat(response.getEvent().get(PROP_STATE)).isEqualTo("WORKFLOW_INSTANCE_CREATED");
        assertThat(response.position()).isGreaterThanOrEqualTo(0L);

        return response.key();
    }

    public long createWorkflowInstance(String bpmnProcessId, DirectBuffer payload)
    {
        return createWorkflowInstance(bpmnProcessId, bufferAsArray(payload));
    }

    public long createWorkflowInstance(String bpmnProcessId, byte[] payload)
    {
        final ExecuteCommandResponse response = apiRule.createCmdRequest()
                .topicName(topicName)
                .partitionId(partitionId)
                .eventTypeWorkflow()
                .command()
                    .put(PROP_STATE, "CREATE_WORKFLOW_INSTANCE")
                    .put(PROP_WORKFLOW_BPMN_PROCESS_ID, bpmnProcessId)
                    .put(PROP_WORKFLOW_PAYLOAD, payload)
                .done()
                .sendAndAwait();

        assertThat(response.getEvent().get(PROP_STATE)).isEqualTo("WORKFLOW_INSTANCE_CREATED");

        return response.key();
    }

    public void completeTaskOfType(String taskType)
    {
        completeTaskOfType(taskType, (byte[]) null);
    }

    public void completeTaskOfType(String taskType, DirectBuffer payload)
    {
        completeTaskOfType(taskType, bufferAsArray(payload));
    }

    public void completeTaskOfType(String taskType, byte[] payload)
    {
        completeTask(taskType, payload, e -> true);
    }

    @SuppressWarnings("rawtypes")
    public void completeTaskOfWorkflowInstance(String taskType, long workflowInstanceKey, byte[] payload)
    {
        completeTask(taskType, payload, e -> ((Map) e.event().get("headers")).get(PROP_WORKFLOW_INSTANCE_KEY).equals(workflowInstanceKey));
    }

    public void completeTask(String taskType, byte[] payload, Predicate<SubscribedEvent> taskEventFilter)
    {
        apiRule.openTaskSubscription(topicName, partitionId, taskType, 1000L).await();

        final SubscribedEvent taskEvent = apiRule
            .subscribedEvents()
            .filter(taskEvents("LOCKED").and(taskType(taskType)).and(taskEventFilter))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected task locked event but not found."));

        final MapBuilder<ExecuteCommandRequestBuilder> mapBuilder = apiRule.createCmdRequest()
                                                                           .topicName(topicName)
                                                                           .partitionId(partitionId)
                                                                           .key(taskEvent.key())
                                                                           .eventTypeTask()
                                                                           .command()
                                                                           .put(PROP_STATE, "COMPLETE")
                                                                           .put("type", taskType)
                                                                           .put("lockOwner", taskEvent.event().get("lockOwner"))
                                                                           .put("headers", taskEvent.event().get("headers"));

        if (payload != null)
        {
            mapBuilder.put("payload", payload);
        }

        final ExecuteCommandResponse response = mapBuilder.done().sendAndAwait();

        assertThat(response.getEvent().get(PROP_STATE)).isEqualTo("COMPLETED");
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

    public static Predicate<SubscribedEvent> state(String eventType)
    {
        return e -> e.event().get(PROP_STATE).equals(eventType);
    }

    public static Predicate<SubscribedEvent> workflowInstanceKey(long workflowInstanceKey)
    {
        return e -> e.event().get(PROP_WORKFLOW_INSTANCE_KEY).equals(workflowInstanceKey);
    }

    public static Predicate<SubscribedEvent> workflowInstanceEvents()
    {
        return e -> e.eventType() == EventType.WORKFLOW_INSTANCE_EVENT;
    }

    public static Predicate<SubscribedEvent> workflowInstanceEvents(String eventType)
    {
        return workflowInstanceEvents().and(state(eventType));
    }

    public static Predicate<SubscribedEvent> workflowInstanceEvents(String eventType, long workflowInstanceKey)
    {
        return workflowInstanceEvents(eventType).and(workflowInstanceKey(workflowInstanceKey));
    }

    public static Predicate<SubscribedEvent> taskEvents()
    {
        return e -> e.eventType() == EventType.TASK_EVENT;
    }

    public static Predicate<SubscribedEvent> taskEvents(String eventType)
    {
        return taskEvents().and(state(eventType));
    }

    public static Predicate<SubscribedEvent> taskType(String taskType)
    {
        return e -> taskType.equals(e.event().get("type"));
    }

    public static Predicate<SubscribedEvent> incidentEvents()
    {
        return e -> e.eventType() == EventType.INCIDENT_EVENT;
    }

    public static Predicate<SubscribedEvent> incidentEvents(String eventType)
    {
        return incidentEvents().and(state(eventType));
    }

    public static Predicate<SubscribedEvent> workflowEvents(String eventType)
    {
        return workflowEvents().and(state(eventType));
    }

    public static Predicate<SubscribedEvent> workflowEvents()
    {
        return e -> e.eventType() == EventType.WORKFLOW_EVENT;
    }

}
