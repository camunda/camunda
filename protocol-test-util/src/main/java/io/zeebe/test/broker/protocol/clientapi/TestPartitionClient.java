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
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.MessageIntent;
import io.zeebe.protocol.intent.SubscriberIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.util.buffer.BufferUtil;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

public class TestPartitionClient {
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

  public TestPartitionClient(final ClientApiRule apiRule, final int partitionId) {
    this.apiRule = apiRule;
    this.partitionId = partitionId;
  }

  public long deploy(final BpmnModelInstance workflow) {
    final ExecuteCommandResponse response = deployWithResponse(workflow);

    assertThat(response.recordType())
        .withFailMessage("Deployment failed: %s", response.rejectionReason())
        .isEqualTo(RecordType.EVENT);

    return response.key();
  }

  public ExecuteCommandResponse deployWithResponse(final byte[] resource) {
    return deployWithResponse(resource, "BPMN_XML", "process.bpmn");
  }

  public ExecuteCommandResponse deployWithResponse(final BpmnModelInstance workflow) {
    return deployWithResponse(workflow, "process.bpmn");
  }

  public ExecuteCommandResponse deployWithResponse(
      final BpmnModelInstance workflow, final String resourceName) {
    final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(outStream, workflow);
    final byte[] resource = outStream.toByteArray();

    return deployWithResponse(resource, "BPMN_XML", resourceName);
  }

  public ExecuteCommandResponse deployWithResponse(
      final byte[] resource, final String resourceType, final String resourceName) {
    final Map<String, Object> deploymentResource = new HashMap<>();
    deploymentResource.put("resource", resource);
    deploymentResource.put("resourceType", resourceType);
    deploymentResource.put("resourceName", resourceName);

    final ExecuteCommandResponse commandResponse =
        apiRule
            .createCmdRequest()
            .partitionId(Protocol.DEPLOYMENT_PARTITION)
            .type(ValueType.DEPLOYMENT, DeploymentIntent.CREATE)
            .command()
            .put(PROP_WORKFLOW_RESOURCES, Collections.singletonList(deploymentResource))
            .put("resouceType", resourceType)
            .done()
            .sendAndAwait();

    return commandResponse;
  }

  public ExecuteCommandResponse createWorkflowInstanceWithResponse(final String bpmnProcessId) {
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
      final String bpmnProcessId, final int version) {
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

  public long createWorkflowInstance(final String bpmnProcessId) {
    final ExecuteCommandResponse response = createWorkflowInstanceWithResponse(bpmnProcessId);

    assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.intent()).isEqualTo(WorkflowInstanceIntent.CREATED);
    assertThat(response.position()).isGreaterThanOrEqualTo(0L);

    return response.key();
  }

  public long createWorkflowInstance(final String bpmnProcessId, final String jsonPayload) {
    return createWorkflowInstance(bpmnProcessId, MsgPackUtil.asMsgPack(jsonPayload));
  }

  public long createWorkflowInstance(final String bpmnProcessId, final DirectBuffer payload) {
    return createWorkflowInstance(bpmnProcessId, bufferAsArray(payload));
  }

  public long createWorkflowInstance(final String bpmnProcessId, final byte[] payload) {
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

  public ExecuteCommandResponse cancelWorkflowInstance(final long key) {
    return apiRule
        .createCmdRequest()
        .partitionId(partitionId)
        .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CANCEL)
        .key(key)
        .command()
        .done()
        .sendAndAwait();
  }

  public ExecuteCommandResponse createJob(final String type) {
    return apiRule
        .createCmdRequest()
        .type(ValueType.JOB, JobIntent.CREATE)
        .command()
        .put("type", type)
        .put("retries", 3)
        .done()
        .sendAndAwait();
  }

  public void completeJobOfType(final String jobType) {
    completeJobOfType(jobType, (byte[]) null);
  }

  public void completeJobOfType(final String jobType, final DirectBuffer payload) {
    completeJobOfType(jobType, bufferAsArray(payload));
  }

  public void completeJobOfType(final String jobType, final byte[] payload) {
    completeJob(jobType, payload, e -> true);
  }

  public void completeJobOfType(final String jobType, final String jsonPayload) {
    completeJob(jobType, MsgPackUtil.asMsgPack(jsonPayload), e -> true);
  }

  @SuppressWarnings("rawtypes")
  public void completeJobOfWorkflowInstance(
      final String jobType, final long workflowInstanceKey, final byte[] payload) {
    completeJob(
        jobType,
        payload,
        e ->
            ((Map) e.value().get("headers"))
                .get(PROP_WORKFLOW_INSTANCE_KEY)
                .equals(workflowInstanceKey));
  }

  public void completeJob(
      final String jobType,
      final byte[] payload,
      final Predicate<SubscribedRecord> jobEventFilter) {
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
      final long sourceRecordPosition, final long key, final Map<String, Object> event) {
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

  public ExecuteCommandResponse failJob(long key, int retries) {
    return apiRule
        .createCmdRequest()
        .type(ValueType.JOB, JobIntent.FAIL)
        .key(key)
        .command()
        .put("retries", retries)
        .done()
        .sendAndAwait();
  }

  public ExecuteCommandResponse updateJobRetries(long key, int retries) {
    return apiRule
        .createCmdRequest()
        .type(ValueType.JOB, JobIntent.UPDATE_RETRIES)
        .key(key)
        .command()
        .put("retries", retries)
        .done()
        .sendAndAwait();
  }

  public ExecuteCommandResponse publishMessage(
      final String messageName, final String correlationKey) {
    return publishMessage(messageName, correlationKey, new byte[0]);
  }

  public ExecuteCommandResponse publishMessage(
      final String messageName, final String correlationKey, final DirectBuffer payload) {
    return publishMessage(messageName, correlationKey, BufferUtil.bufferAsArray(payload));
  }

  public ExecuteCommandResponse publishMessage(
      final String messageName,
      final String correlationKey,
      final DirectBuffer payload,
      final long ttl) {
    return publishMessage(messageName, correlationKey, BufferUtil.bufferAsArray(payload), ttl);
  }

  public ExecuteCommandResponse publishMessage(
      final String messageName, final String correlationKey, final byte[] payload) {
    return publishMessage(messageName, correlationKey, payload, Duration.ofHours(1).toMillis());
  }

  public ExecuteCommandResponse publishMessage(
      final String messageName, final String correlationKey, final byte[] payload, final long ttl) {
    return apiRule
        .createCmdRequest()
        .type(ValueType.MESSAGE, MessageIntent.PUBLISH)
        .command()
        .put("name", messageName)
        .put("correlationKey", correlationKey)
        .put("timeToLive", ttl)
        .put("payload", payload)
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
      assertThat(response.intent()).isEqualTo(SubscriberIntent.SUBSCRIBED);

      partitionIdsOfOpenSubscription.add(partitionId);
    }
  }

  public void closeTopicSubscription() {
    partitionIdsOfOpenSubscription.clear();
  }

  public static Predicate<SubscribedRecord> intent(final Intent intent) {
    return e -> e.intent() == intent;
  }

  public static Predicate<SubscribedRecord> recordType(final RecordType recordType) {
    return e -> e.recordType() == recordType;
  }

  public static Predicate<SubscribedRecord> workflowInstanceKey(final long workflowInstanceKey) {
    return e -> e.value().get(PROP_WORKFLOW_INSTANCE_KEY).equals(workflowInstanceKey);
  }

  public static Predicate<SubscribedRecord> workflowInstanceRecords() {
    return e -> e.valueType() == ValueType.WORKFLOW_INSTANCE;
  }

  public static Predicate<SubscribedRecord> workflowInstanceRecords(final Intent intent) {
    return workflowInstanceRecords().and(intent(intent));
  }

  public static Predicate<SubscribedRecord> workflowInstanceEvents(final Intent intent) {
    return workflowInstanceRecords().and(recordType(RecordType.EVENT)).and(intent(intent));
  }

  public static Predicate<SubscribedRecord> workflowInstanceRejections(final Intent intent) {
    return workflowInstanceRecords()
        .and(recordType(RecordType.COMMAND_REJECTION))
        .and(intent(intent));
  }

  public static Predicate<SubscribedRecord> workflowInstanceRecords(
      final Intent intent, final long workflowInstanceKey) {
    return workflowInstanceRecords(intent).and(workflowInstanceKey(workflowInstanceKey));
  }

  public static Predicate<SubscribedRecord> jobRecords() {
    return e -> e.valueType() == ValueType.JOB;
  }

  public static Predicate<SubscribedRecord> jobRecords(final Intent intent) {
    return jobRecords().and(intent(intent));
  }

  public static Predicate<SubscribedRecord> jobType(final String jobType) {
    return e -> jobType.equals(e.value().get("type"));
  }

  public static Predicate<SubscribedRecord> incidentEvents() {
    return e -> e.valueType() == ValueType.INCIDENT;
  }

  public static Predicate<SubscribedRecord> incidentRecords(final Intent intent) {
    return incidentEvents().and(intent(intent));
  }

  public static Predicate<SubscribedRecord> incidentRecords(
      final Intent intent, final long workflowInstanceKey) {
    return incidentRecords(intent).and(workflowInstanceKey(workflowInstanceKey));
  }

  //////////////////////////////////////////////////////////////////////////////
  // short cuts ////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  public SubscribedRecord receiveFirstIncidentEvent(final IncidentIntent intent) {
    return receiveEvents().ofTypeIncident().withIntent(intent).getFirst();
  }

  public SubscribedRecord receiveFirstIncidentEvent(final long wfInstanceKey, final Intent intent) {
    return receiveEvents()
        .ofTypeIncident()
        .withIntent(intent)
        .filter(r -> (Long) r.value().get("workflowInstanceKey") == wfInstanceKey)
        .findFirst()
        .get();
  }

  public SubscribedRecord receiveFirstIncidentCommand(final IncidentIntent intent) {
    return receiveCommands().ofTypeIncident().withIntent(intent).getFirst();
  }

  public SubscribedRecord receiveFirstDeploymentEvent(
      final DeploymentIntent intent, final long deploymentKey) {
    return receiveEvents()
        .ofTypeDeployment()
        .withIntent(intent)
        .filter(d -> d.key() == deploymentKey)
        .findFirst()
        .orElseThrow(() -> new AssertionError("no event received"));
  }

  public SubscribedRecord receiveFirstWorkflowInstanceEvent(final WorkflowInstanceIntent intent) {
    return receiveEvents().ofTypeWorkflowInstance().withIntent(intent).getFirst();
  }

  public SubscribedRecord receiveElementInState(
      final String elementId, final WorkflowInstanceIntent intent) {
    return receiveEvents()
        .ofTypeWorkflowInstance()
        .withIntent(intent)
        .filter(r -> elementId.equals(r.value().get("activityId")))
        .findFirst()
        .get();
  }

  public SubscribedRecord receiveElementInState(
      final long workflowInstanceKey, final String elementId, final WorkflowInstanceIntent intent) {
    return receiveEvents()
        .ofTypeWorkflowInstance()
        .withIntent(intent)
        .filter(r -> (Long) r.value.get("workflowInstanceKey") == workflowInstanceKey)
        .filter(r -> elementId.equals(r.value().get("activityId")))
        .findFirst()
        .get();
  }

  public SubscribedRecord receiveFirstWorkflowInstanceCommand(final WorkflowInstanceIntent intent) {
    return receiveCommands().ofTypeWorkflowInstance().withIntent(intent).getFirst();
  }

  public SubscribedRecord receiveFirstWorkflowInstanceEvent(
      final long wfInstanceKey, final Intent intent) {
    return receiveEvents()
        .ofTypeWorkflowInstance()
        .withIntent(intent)
        .filter(r -> (Long) r.value().get("workflowInstanceKey") == wfInstanceKey)
        .findFirst()
        .get();
  }

  public SubscribedRecord receiveFirstWorkflowInstanceEvent(
      final long wfInstanceKey, final String activityId, final Intent intent) {
    return receiveEvents()
        .ofTypeWorkflowInstance()
        .withIntent(intent)
        .filter(r -> (Long) r.value().get("workflowInstanceKey") == wfInstanceKey)
        .filter(r -> activityId.equals(r.value().get("activityId")))
        .findFirst()
        .get();
  }

  public List<SubscribedRecord> receiveElementInstancesInState(Intent intent, int expectedNumber) {
    return receiveEvents()
        .ofTypeWorkflowInstance()
        .withIntent(intent)
        .limit(expectedNumber)
        .collect(Collectors.toList());
  }

  public SubscribedRecord receiveElementInstanceInState(long key, Intent intent) {
    return receiveEvents()
        .ofTypeWorkflowInstance()
        .withIntent(intent)
        .filter(r -> r.key() == key)
        .limit(1)
        .findFirst()
        .get();
  }

  public SubscribedRecord receiveFirstJobEvent(final JobIntent intent) {
    return receiveEvents().ofTypeJob().withIntent(intent).getFirst();
  }

  public SubscribedRecord receiveFirstJobCommand(final JobIntent intent) {
    return receiveCommands().ofTypeJob().withIntent(intent).getFirst();
  }
}
