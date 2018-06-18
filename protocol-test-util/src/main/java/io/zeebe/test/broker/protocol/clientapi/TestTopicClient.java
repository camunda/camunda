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

import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.*;
import java.util.*;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;

public class TestTopicClient {
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

  public TestTopicClient(final ClientApiRule apiRule, final int partitionId) {
    this.apiRule = apiRule;
    this.partitionId = partitionId;
  }

  public long deploy(final WorkflowDefinition workflow) {
    return deploy(ClientApiRule.DEFAULT_TOPIC_NAME, workflow);
  }

  public long deploy(String topic, final WorkflowDefinition workflow) {
    final ExecuteCommandResponse response = deployWithResponse(topic, workflow);

    assertThat(response.recordType())
        .withFailMessage("Deployment failed: %s", response.getValue().get("errorMessage"))
        .isEqualTo(RecordType.EVENT);

    return response.key();
  }

  public ExecuteCommandResponse deployWithResponse(String topic, byte[] resource) {
    return deployWithResponse(topic, resource, "BPMN_XML", "process.bpmn");
  }

  public ExecuteCommandResponse deployWithResponse(
      String topic, final WorkflowDefinition workflow) {
    return deployWithResponse(topic, workflow, "process.bpmn");
  }

  public ExecuteCommandResponse deployWithResponse(
      String topic, final WorkflowDefinition workflow, String resourceName) {
    final byte[] resource = Bpmn.convertToString(workflow).getBytes(UTF_8);

    return deployWithResponse(topic, resource, "BPMN_XML", resourceName);
  }

  public ExecuteCommandResponse deployWithResponse(
      String topic, final byte[] resource, final String resourceType, final String resourceName) {
    final Map<String, Object> deploymentResource = new HashMap<>();
    deploymentResource.put("resource", resource);
    deploymentResource.put("resourceType", resourceType);
    deploymentResource.put("resourceName", resourceName);

    return apiRule
        .createCmdRequest()
        .partitionId(Protocol.SYSTEM_PARTITION)
        .type(ValueType.DEPLOYMENT, DeploymentIntent.CREATE)
        .command()
        .put("topicName", topic)
        .put(PROP_WORKFLOW_RESOURCES, Collections.singletonList(deploymentResource))
        .put("resouceType", resourceType)
        .done()
        .sendAndAwait();
  }

  public ExecuteCommandResponse createWorkflowInstanceWithResponse(String bpmnProcessId) {
    return apiRule
        .createCmdRequest()
        .partitionId(partitionId)
        .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CREATE)
        .command()
        .put(PROP_WORKFLOW_BPMN_PROCESS_ID, bpmnProcessId)
        .done()
        .sendAndAwait();
  }

  public ExecuteCommandResponse createWorkflowInstanceWithResponse(
      String bpmnProcessId, int version) {
    return apiRule
        .createCmdRequest()
        .partitionId(partitionId)
        .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CREATE)
        .command()
        .put(PROP_WORKFLOW_BPMN_PROCESS_ID, bpmnProcessId)
        .put(PROP_WORKFLOW_VERSION, version)
        .done()
        .sendAndAwait();
  }

  public long createWorkflowInstance(String bpmnProcessId) {
    final ExecuteCommandResponse response = createWorkflowInstanceWithResponse(bpmnProcessId);

    assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.intent()).isEqualTo(WorkflowInstanceIntent.CREATED);
    assertThat(response.position()).isGreaterThanOrEqualTo(0L);

    return response.key();
  }

  public long createWorkflowInstance(String bpmnProcessId, DirectBuffer payload) {
    return createWorkflowInstance(bpmnProcessId, bufferAsArray(payload));
  }

  public long createWorkflowInstance(String bpmnProcessId, byte[] payload) {
    final ExecuteCommandResponse response =
        apiRule
            .createCmdRequest()
            .partitionId(partitionId)
            .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CREATE)
            .command()
            .put(PROP_WORKFLOW_BPMN_PROCESS_ID, bpmnProcessId)
            .put(PROP_WORKFLOW_PAYLOAD, payload)
            .done()
            .sendAndAwait();

    assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.intent()).isEqualTo(WorkflowInstanceIntent.CREATED);

    return response.key();
  }

  public ExecuteCommandResponse createJob(String type) {
    return apiRule
        .createCmdRequest()
        .type(ValueType.JOB, JobIntent.CREATE)
        .command()
        .put("type", type)
        .put("retries", 3)
        .done()
        .sendAndAwait();
  }

  public void completeJobOfType(String jobType) {
    completeJobOfType(jobType, (byte[]) null);
  }

  public void completeJobOfType(String jobType, DirectBuffer payload) {
    completeJobOfType(jobType, bufferAsArray(payload));
  }

  public void completeJobOfType(String jobType, byte[] payload) {
    completeJob(jobType, payload, e -> true);
  }

  @SuppressWarnings("rawtypes")
  public void completeJobOfWorkflowInstance(
      String jobType, long workflowInstanceKey, byte[] payload) {
    completeJob(
        jobType,
        payload,
        e ->
            ((Map) e.value().get("headers"))
                .get(PROP_WORKFLOW_INSTANCE_KEY)
                .equals(workflowInstanceKey));
  }

  public void completeJob(
      String jobType, byte[] payload, Predicate<SubscribedRecord> jobEventFilter) {
    apiRule.openJobSubscription(partitionId, jobType, 1000L).await();

    final SubscribedRecord jobEvent =
        apiRule
            .subscribedEvents()
            .filter(jobRecords(JobIntent.ACTIVATED).and(jobType(jobType)).and(jobEventFilter))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected job locked event but not found."));

    final Map<String, Object> event = new HashMap<>(jobEvent.value());
    if (payload != null) {
      event.put("payload", payload);
    } else {
      event.remove("payload");
    }

    final ExecuteCommandResponse response = completeJob(jobEvent.position(), jobEvent.key(), event);

    assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.intent()).isEqualTo(JobIntent.COMPLETED);
  }

  public ExecuteCommandResponse completeJob(
      long sourceRecordPosition, long key, Map<String, Object> event) {
    return apiRule
        .createCmdRequest()
        .type(ValueType.JOB, JobIntent.COMPLETE)
        .key(key)
        .sourceRecordPosition(sourceRecordPosition)
        .command()
        .putAll(event)
        .done()
        .sendAndAwait();
  }

  public ExecuteCommandResponse failJob(
      long sourceRecordPosition, long key, Map<String, Object> event) {
    return apiRule
        .createCmdRequest()
        .type(ValueType.JOB, JobIntent.FAIL)
        .sourceRecordPosition(sourceRecordPosition)
        .key(key)
        .command()
        .putAll(event)
        .done()
        .sendAndAwait();
  }

  public ExecuteCommandResponse updateJobRetries(
      long sourceRecordPosition, long key, Map<String, Object> event) {
    return apiRule
        .createCmdRequest()
        .type(ValueType.JOB, JobIntent.UPDATE_RETRIES)
        .sourceRecordPosition(sourceRecordPosition)
        .key(key)
        .command()
        .putAll(event)
        .done()
        .sendAndAwait();
  }

  /**
   * @return an infinite stream of received subscribed events; make sure to use short-circuiting
   *     operations to reduce it to a finite stream
   */
  public SubscribedRecordStream receiveEvents() {
    return receiveRecords().onlyEvents();
  }

  public SubscribedRecordStream receiveCommands() {
    return receiveRecords().onlyCommands();
  }

  public SubscribedRecordStream receiveRejections() {
    return receiveRecords().onlyRejections();
  }

  public SubscribedRecordStream receiveRecords() {
    ensureOpenTopicSubscription();

    return new SubscribedRecordStream(
        apiRule
            .moveMessageStreamToHead()
            .subscribedEvents()
            .filter(s -> s.subscriptionType() == SubscriptionType.TOPIC_SUBSCRIPTION)
            .filter(s -> s.partitionId() == partitionId));
  }

  private void ensureOpenTopicSubscription() {
    if (!partitionIdsOfOpenSubscription.contains(partitionId)) {
      final ExecuteCommandResponse response =
          apiRule.openTopicSubscription(partitionId, "test", 0).await();
      assertThat(response.key()).isGreaterThanOrEqualTo(0);

      partitionIdsOfOpenSubscription.add(partitionId);
    }
  }

  public static Predicate<SubscribedRecord> intent(Intent intent) {
    return e -> e.intent() == intent;
  }

  public static Predicate<SubscribedRecord> recordType(RecordType recordType) {
    return e -> e.recordType() == recordType;
  }

  public static Predicate<SubscribedRecord> workflowInstanceKey(long workflowInstanceKey) {
    return e -> e.value().get(PROP_WORKFLOW_INSTANCE_KEY).equals(workflowInstanceKey);
  }

  public static Predicate<SubscribedRecord> workflowInstanceRecords() {
    return e -> e.valueType() == ValueType.WORKFLOW_INSTANCE;
  }

  public static Predicate<SubscribedRecord> workflowInstanceRecords(Intent intent) {
    return workflowInstanceRecords().and(intent(intent));
  }

  public static Predicate<SubscribedRecord> workflowInstanceEvents(Intent intent) {
    return workflowInstanceRecords().and(recordType(RecordType.EVENT)).and(intent(intent));
  }

  public static Predicate<SubscribedRecord> workflowInstanceRejections(Intent intent) {
    return workflowInstanceRecords()
        .and(recordType(RecordType.COMMAND_REJECTION))
        .and(intent(intent));
  }

  public static Predicate<SubscribedRecord> workflowInstanceRecords(
      Intent intent, long workflowInstanceKey) {
    return workflowInstanceRecords(intent).and(workflowInstanceKey(workflowInstanceKey));
  }

  public static Predicate<SubscribedRecord> jobRecords() {
    return e -> e.valueType() == ValueType.JOB;
  }

  public static Predicate<SubscribedRecord> jobRecords(Intent intent) {
    return jobRecords().and(intent(intent));
  }

  public static Predicate<SubscribedRecord> jobType(String jobType) {
    return e -> jobType.equals(e.value().get("type"));
  }

  public static Predicate<SubscribedRecord> incidentEvents() {
    return e -> e.valueType() == ValueType.INCIDENT;
  }

  public static Predicate<SubscribedRecord> incidentRecords(Intent intent) {
    return incidentEvents().and(intent(intent));
  }

  public static Predicate<SubscribedRecord> incidentRecords(
      Intent intent, long workflowInstanceKey) {
    return incidentRecords(intent).and(workflowInstanceKey(workflowInstanceKey));
  }

  //////////////////////////////////////////////////////////////////////////////
  // short cuts ////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  public SubscribedRecord receiveFirstIncidentEvent(IncidentIntent intent) {
    return receiveEvents().ofTypeIncident().withIntent(intent).getFirst();
  }

  public SubscribedRecord receiveFirstIncidentEvent(long wfInstanceKey, Intent intent) {
    return receiveEvents()
        .ofTypeIncident()
        .withIntent(intent)
        .filter(r -> (Long) r.value().get("workflowInstanceKey") == wfInstanceKey)
        .findFirst()
        .get();
  }

  public SubscribedRecord receiveFirstIncidentCommand(IncidentIntent intent) {
    return receiveCommands().ofTypeIncident().withIntent(intent).getFirst();
  }

  public SubscribedRecord receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent intent) {
    return receiveEvents().ofTypeWorkflowInstance().withIntent(intent).getFirst();
  }

  public SubscribedRecord receiveFirstWorkflowInstanceCommand(WorkflowInstanceIntent intent) {
    return receiveCommands().ofTypeWorkflowInstance().withIntent(intent).getFirst();
  }

  public SubscribedRecord receiveFirstWorkflowInstanceEvent(long wfInstanceKey, Intent intent) {
    return receiveEvents()
        .ofTypeWorkflowInstance()
        .withIntent(intent)
        .filter(r -> (Long) r.value().get("workflowInstanceKey") == wfInstanceKey)
        .findFirst()
        .get();
  }

  public SubscribedRecord receiveFirstJobEvent(JobIntent intent) {
    return receiveEvents().ofTypeJob().withIntent(intent).getFirst();
  }

  public SubscribedRecord receiveFirstJobCommand(JobIntent intent) {
    return receiveCommands().ofTypeJob().withIntent(intent).getFirst();
  }
}
