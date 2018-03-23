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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.agrona.DirectBuffer;

import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.clientapi.SubscriptionType;

public class TestTopicClient
{

    public static final String PROP_STATE = "state";

    // workflow related properties

    public static final String PROP_WORKFLOW_BPMN_PROCESS_ID = "bpmnProcessId";
    public static final String PROP_WORKFLOW_RESOURCES = "resources";
    public static final String PROP_WORKFLOW_VERSION = "version";
    public static final String PROP_WORKFLOW_PAYLOAD = "payload";
    public static final String PROP_WORKFLOW_INSTANCE_KEY = "workflowInstanceKey";
    public static final String PROP_WORKFLOW_KEY = "workflowKey";

    private final ClientApiRule apiRule;
    private final int partitionId;

    private final List<Integer> partitionIdsOfOpenSubscription = new ArrayList<>();

    public TestTopicClient(final ClientApiRule apiRule, final int partitionId)
    {
        this.apiRule = apiRule;
        this.partitionId = partitionId;
    }

    public long deploy(final WorkflowDefinition workflow)
    {
        return deploy(ClientApiRule.DEFAULT_TOPIC_NAME, workflow);
    }

    public long deploy(String topic, final WorkflowDefinition workflow)
    {
        final ExecuteCommandResponse response = deployWithResponse(topic, workflow);

        assertThat(response.getEvent().get(PROP_STATE))
            .withFailMessage("Deployment failed: %s", response.getEvent().get("errorMessage"))
            .isEqualTo("CREATED");

        return response.key();
    }

    public ExecuteCommandResponse deployWithResponse(String topic, final WorkflowDefinition workflow)
    {
        final byte[] resource = Bpmn.convertToString(workflow).getBytes(UTF_8);

        return deployWithResponse(topic, resource, "BPMN_XML", "process.bpmn");
    }

    public ExecuteCommandResponse deployWithResponse(String topic, final byte[] resource, final String resourceType, final String resourceName)
    {
        final Map<String, Object> deploymentResource = new HashMap<>();
        deploymentResource.put("resource", resource);
        deploymentResource.put("resourceType", resourceType);
        deploymentResource.put("resourceName", resourceName);

        return apiRule.createCmdRequest()
                .partitionId(Protocol.SYSTEM_PARTITION)
                .eventType(EventType.DEPLOYMENT_EVENT)
                .command()
                    .put(PROP_STATE, "CREATE")
                    .put("topicName", topic)
                    .put(PROP_WORKFLOW_RESOURCES, Collections.singletonList(deploymentResource))
                    .put("resouceType", resourceType)
                .done()
                .sendAndAwait();
    }

    public ExecuteCommandResponse createWorkflowInstanceWithResponse(String bpmnProcessId)
    {
        return apiRule.createCmdRequest()
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
        final ExecuteCommandResponse response = createWorkflowInstanceWithResponse(bpmnProcessId);

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

    public ExecuteCommandResponse createTask(String type)
    {
        return apiRule.createCmdRequest()
                .eventTypeTask()
                .command()
                    .put("state", "CREATE")
                    .put("type", type)
                    .put("retries", 3)
                .done()
                .sendAndAwait();
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
        apiRule.openTaskSubscription(partitionId, taskType, 1000L).await();

        final SubscribedEvent taskEvent = apiRule
            .subscribedEvents()
            .filter(taskEvents("LOCKED").and(taskType(taskType)).and(taskEventFilter))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected task locked event but not found."));

        final Map<String, Object> event = new HashMap<>(taskEvent.event());
        if (payload != null)
        {
            event.put("payload", payload);
        }
        else
        {
            event.remove("payload");
        }

        final ExecuteCommandResponse response = completeTask(taskEvent.key(), event);

        assertThat(response.getEvent().get(PROP_STATE)).isEqualTo("COMPLETED");
    }

    public ExecuteCommandResponse completeTask(long key, Map<String, Object> event)
    {
        return apiRule.createCmdRequest()
            .eventTypeTask()
            .key(key)
            .command()
                .putAll(event)
                .put("state", "COMPLETE")
            .done()
            .sendAndAwait();
    }

    public ExecuteCommandResponse failTask(long key, Map<String, Object> event)
    {
        return apiRule.createCmdRequest()
            .eventTypeTask()
            .key(key)
            .command()
                .putAll(event)
                .put("state", "FAIL")
            .done()
            .sendAndAwait();
    }

    public ExecuteCommandResponse updateTaskRetries(long key, Map<String, Object> event)
    {
        return apiRule.createCmdRequest()
            .eventTypeTask()
            .key(key)
            .command()
                .putAll(event)
                .put("state", "UPDATE_RETRIES")
            .done()
            .sendAndAwait();
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
                .filter(s -> s.subscriptionType() == SubscriptionType.TOPIC_SUBSCRIPTION)
                .filter(s -> s.partitionId() == partitionId)
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
        if (!partitionIdsOfOpenSubscription.contains(partitionId))
        {
            final ExecuteCommandResponse response = apiRule.openTopicSubscription(partitionId, "test", 0).await();
            assertThat(response.key()).isGreaterThanOrEqualTo(0);

            partitionIdsOfOpenSubscription.add(partitionId);
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

    public static Predicate<SubscribedEvent> incidentEvents(String eventType, long workflowInstanceKey)
    {
        return incidentEvents(eventType).and(workflowInstanceKey(workflowInstanceKey));
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
