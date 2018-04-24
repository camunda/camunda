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

import org.agrona.DirectBuffer;

import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.Intent;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.protocol.clientapi.ValueType;

public class TestTopicClient
{
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

        assertThat(response.recordType())
            .withFailMessage("Deployment failed: %s", response.getValue().get("errorMessage"))
            .isEqualTo(RecordType.EVENT);

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
                .type(ValueType.DEPLOYMENT, Intent.CREATE)
                .command()
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
                      .type(ValueType.WORKFLOW_INSTANCE, Intent.CREATE)
                      .command()
                          .put(PROP_WORKFLOW_BPMN_PROCESS_ID, bpmnProcessId)
                      .done()
                      .sendAndAwait();
    }

    public ExecuteCommandResponse createWorkflowInstanceWithResponse(String bpmnProcessId, int version)
    {
        return apiRule.createCmdRequest()
                      .partitionId(partitionId)
                      .eventTypeWorkflow()
                      .command()
                      .put(PROP_STATE, "CREATE_WORKFLOW_INSTANCE")
                      .put(PROP_WORKFLOW_BPMN_PROCESS_ID, bpmnProcessId)
                      .put(PROP_WORKFLOW_VERSION, version)
                      .done()
                      .sendAndAwait();
    }

    public long createWorkflowInstance(String bpmnProcessId)
    {
        final ExecuteCommandResponse response = createWorkflowInstanceWithResponse(bpmnProcessId);

        assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
        assertThat(response.intent()).isEqualTo(Intent.CREATED);
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
                .type(ValueType.WORKFLOW_INSTANCE, Intent.CREATE)
                .command()
                    .put(PROP_WORKFLOW_BPMN_PROCESS_ID, bpmnProcessId)
                    .put(PROP_WORKFLOW_PAYLOAD, payload)
                .done()
                .sendAndAwait();

        assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
        assertThat(response.intent()).isEqualTo(Intent.CREATED);

        return response.key();
    }

    public ExecuteCommandResponse createTask(String type)
    {
        return apiRule.createCmdRequest()
                .type(ValueType.TASK, Intent.CREATE)
                .command()
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
        completeTask(taskType, payload, e -> ((Map) e.value().get("headers")).get(PROP_WORKFLOW_INSTANCE_KEY).equals(workflowInstanceKey));
    }

    public void completeTask(String taskType, byte[] payload, Predicate<SubscribedRecord> taskEventFilter)
    {
        apiRule.openTaskSubscription(partitionId, taskType, 1000L).await();

        final SubscribedRecord taskEvent = apiRule
            .subscribedEvents()
            .filter(taskRecords(Intent.LOCKED).and(taskType(taskType)).and(taskEventFilter))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected task locked event but not found."));

        final Map<String, Object> event = new HashMap<>(taskEvent.value());
        if (payload != null)
        {
            event.put("payload", payload);
        }
        else
        {
            event.remove("payload");
        }

        final ExecuteCommandResponse response = completeTask(taskEvent.key(), event);

        assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
        assertThat(response.intent()).isEqualTo(Intent.COMPLETED);
    }

    public ExecuteCommandResponse completeTask(long key, Map<String, Object> event)
    {
        return apiRule.createCmdRequest()
            .type(ValueType.TASK, Intent.COMPLETE)
            .key(key)
            .command()
                .putAll(event)
            .done()
            .sendAndAwait();
    }

    public ExecuteCommandResponse failTask(long key, Map<String, Object> event)
    {
        return apiRule.createCmdRequest()
            .type(ValueType.TASK, Intent.FAIL)
            .key(key)
            .command()
                .putAll(event)
            .done()
            .sendAndAwait();
    }

    public ExecuteCommandResponse updateTaskRetries(long key, Map<String, Object> event)
    {
        return apiRule.createCmdRequest()
            .type(ValueType.TASK, Intent.UPDATE_RETRIES)
            .key(key)
            .command()
                .putAll(event)
            .done()
            .sendAndAwait();
    }

    /**
     * @return an infinite stream of received subscribed events; make sure to use short-circuiting operations
     *   to reduce it to a finite stream
     */
    public SubscribedRecordStream receiveEvents()
    {
        return receiveRecords()
                .onlyEvents();
    }

    public SubscribedRecordStream receiveCommands()
    {
        return receiveRecords()
                .onlyCommands();
    }

    public SubscribedRecordStream receiveRejections()
    {
        return receiveRecords()
                .onlyRejections();
    }

    public SubscribedRecordStream receiveRecords()
    {
        ensureOpenTopicSubscription();

        return new SubscribedRecordStream(apiRule
                .moveMessageStreamToHead()
                .subscribedEvents()
                .filter(s -> s.subscriptionType() == SubscriptionType.TOPIC_SUBSCRIPTION)
                .filter(s -> s.partitionId() == partitionId));
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

    public static Predicate<SubscribedRecord> intent(Intent intent)
    {
        return e -> e.intent() == intent;
    }

    public static Predicate<SubscribedRecord> recordType(RecordType recordType)
    {
        return e -> e.recordType() == recordType;
    }

    public static Predicate<SubscribedRecord> workflowInstanceKey(long workflowInstanceKey)
    {
        return e -> e.value().get(PROP_WORKFLOW_INSTANCE_KEY).equals(workflowInstanceKey);
    }

    public static Predicate<SubscribedRecord> workflowInstanceRecords()
    {
        return e -> e.valueType() == ValueType.WORKFLOW_INSTANCE;
    }

    public static Predicate<SubscribedRecord> workflowInstanceRecords(Intent intent)
    {
        return workflowInstanceRecords().and(intent(intent));
    }

    public static Predicate<SubscribedRecord> workflowInstanceEvents(Intent intent)
    {
        return workflowInstanceRecords().and(recordType(RecordType.EVENT)).and(intent(intent));
    }

    public static Predicate<SubscribedRecord> workflowInstanceRejections(Intent intent)
    {
        return workflowInstanceRecords().and(recordType(RecordType.COMMAND_REJECTION)).and(intent(intent));
    }

    public static Predicate<SubscribedRecord> workflowInstanceRecords(Intent intent, long workflowInstanceKey)
    {
        return workflowInstanceRecords(intent).and(workflowInstanceKey(workflowInstanceKey));
    }

    public static Predicate<SubscribedRecord> taskRecords()
    {
        return e -> e.valueType() == ValueType.TASK;
    }

    public static Predicate<SubscribedRecord> taskRecords(Intent intent)
    {
        return taskRecords().and(intent(intent));
    }

    public static Predicate<SubscribedRecord> taskType(String taskType)
    {
        return e -> taskType.equals(e.value().get("type"));
    }

    public static Predicate<SubscribedRecord> incidentEvents()
    {
        return e -> e.valueType() == ValueType.INCIDENT;
    }

    public static Predicate<SubscribedRecord> incidentRecords(Intent intent)
    {
        return incidentEvents().and(intent(intent));
    }

    public static Predicate<SubscribedRecord> incidentRecords(Intent intent, long workflowInstanceKey)
    {
        return incidentRecords(intent).and(workflowInstanceKey(workflowInstanceKey));
    }

    public static Predicate<SubscribedRecord> workflowRecords(Intent intent)
    {
        return workflowRecords().and(intent(intent));
    }
}
