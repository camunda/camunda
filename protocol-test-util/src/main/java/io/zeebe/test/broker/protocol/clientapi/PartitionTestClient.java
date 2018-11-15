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

import static io.zeebe.protocol.intent.JobIntent.ACTIVATED;
import static io.zeebe.util.buffer.BufferUtil.bufferAsArray;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.DeploymentRecordValue;
import io.zeebe.exporter.record.value.IncidentRecordValue;
import io.zeebe.exporter.record.value.JobRecordValue;
import io.zeebe.exporter.record.value.MessageRecordValue;
import io.zeebe.exporter.record.value.TimerRecordValue;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.MessageIntent;
import io.zeebe.protocol.intent.TimerIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.record.DeploymentRecordStream;
import io.zeebe.test.util.record.IncidentRecordStream;
import io.zeebe.test.util.record.JobBatchRecordStream;
import io.zeebe.test.util.record.JobRecordStream;
import io.zeebe.test.util.record.MessageRecordStream;
import io.zeebe.test.util.record.MessageSubscriptionRecordStream;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.TimerRecordStream;
import io.zeebe.test.util.record.WorkflowInstanceRecordStream;
import io.zeebe.test.util.record.WorkflowInstanceSubscriptionRecordStream;
import io.zeebe.util.buffer.BufferUtil;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

public class PartitionTestClient {
  // workflow related properties

  public static final String PROP_WORKFLOW_BPMN_PROCESS_ID = "bpmnProcessId";
  public static final String PROP_WORKFLOW_RESOURCES = "resources";
  public static final String PROP_WORKFLOW_VERSION = "version";
  public static final String PROP_WORKFLOW_PAYLOAD = "payload";
  public static final String PROP_WORKFLOW_INSTANCE_KEY = "workflowInstanceKey";
  public static final String PROP_WORKFLOW_KEY = "workflowKey";

  private final ClientApiRule apiRule;
  private final int partitionId;

  public PartitionTestClient(final ClientApiRule apiRule, final int partitionId) {
    this.apiRule = apiRule;
    this.partitionId = partitionId;
  }

  public long deploy(final BpmnModelInstance workflow) {
    final ExecuteCommandResponse response = deployWithResponse(workflow);

    assertThat(response.getRecordType())
        .withFailMessage("Deployment failed: %s", response.getRejectionReason())
        .isEqualTo(RecordType.EVENT);

    return response.getKey();
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
            .put("resourceType", resourceType)
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

  public long createWorkflowInstance(final String bpmnProcessId) {
    final ExecuteCommandResponse response = createWorkflowInstanceWithResponse(bpmnProcessId);

    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(WorkflowInstanceIntent.ELEMENT_READY);

    return response.getKey();
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

    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(WorkflowInstanceIntent.ELEMENT_READY);

    return response.getKey();
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

  public void updatePayload(final long elementInstanceKey, final String jsonPayload) {
    updatePayload(elementInstanceKey, MsgPackUtil.asMsgPack(jsonPayload));
  }

  public void updatePayload(final long elementInstanceKey, final byte[] payload) {
    final ExecuteCommandResponse response =
        apiRule
            .createCmdRequest()
            .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.UPDATE_PAYLOAD)
            .key(elementInstanceKey)
            .command()
            .put("payload", payload)
            .done()
            .sendAndAwait();

    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(WorkflowInstanceIntent.PAYLOAD_UPDATED);
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
    completeJobOfType(jobType, "{}");
  }

  public void completeJobOfType(final String jobType, final byte[] payload) {
    completeJob(jobType, payload, e -> true);
  }

  public void completeJobOfType(final String jobType, final String jsonPayload) {
    completeJob(jobType, MsgPackUtil.asMsgPack(jsonPayload), e -> true);
  }

  public void completeJobOfWorkflowInstance(
      final String jobType, final long workflowInstanceKey, final byte[] payload) {
    completeJob(
        jobType,
        payload,
        e -> e.getValue().getHeaders().getWorkflowInstanceKey() == workflowInstanceKey);
  }

  public ExecuteCommandResponse completeJob(long key, String payload) {
    return completeJob(key, MsgPackUtil.asMsgPack(payload));
  }

  public ExecuteCommandResponse completeJob(long key, byte[] payload) {
    return apiRule
        .createCmdRequest()
        .type(ValueType.JOB, JobIntent.COMPLETE)
        .key(key)
        .command()
        .put("payload", payload)
        .done()
        .sendAndAwait();
  }

  public void completeJob(
      final String jobType,
      final byte[] payload,
      final Predicate<Record<JobRecordValue>> jobEventFilter) {
    apiRule.activateJobs(partitionId, jobType, 1000L).await();

    final Record<JobRecordValue> jobEvent =
        receiveJobs()
            .withIntent(ACTIVATED)
            .withType(jobType)
            .filter(jobEventFilter)
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected job locked event but not found."));

    final ExecuteCommandResponse response = completeJob(jobEvent.getKey(), payload);

    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(JobIntent.COMPLETED);
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

  public ExecuteCommandResponse failJobWithMessage(long key, int retries, String errorMessage) {
    return apiRule
        .createCmdRequest()
        .type(ValueType.JOB, JobIntent.FAIL)
        .key(key)
        .command()
        .put("retries", retries)
        .put("errorMessage", errorMessage)
        .done()
        .sendAndAwait();
  }

  public ExecuteCommandResponse createJobIncidentWithJobErrorMessage(
      long key, String errorMessage) {
    return failJobWithMessage(key, 0, errorMessage);
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
        .partitionId(partitionId)
        .type(ValueType.MESSAGE, MessageIntent.PUBLISH)
        .command()
        .put("name", messageName)
        .put("correlationKey", correlationKey)
        .put("timeToLive", ttl)
        .put("payload", payload)
        .done()
        .sendAndAwait();
  }

  /////////////////////////////////////////////
  // INCIDENTS //////////////////////////////
  /////////////////////////////////////////////

  public IncidentRecordStream receiveIncidents() {
    return RecordingExporter.incidentRecords().withPartitionId(partitionId);
  }

  public Record<IncidentRecordValue> receiveFirstIncidentEvent(final IncidentIntent intent) {
    return receiveIncidents().withIntent(intent).getFirst();
  }

  public Record<IncidentRecordValue> receiveFirstIncidentEvent(
      final long wfInstanceKey, final Intent intent) {
    return receiveIncidents().withIntent(intent).withWorkflowInstanceKey(wfInstanceKey).getFirst();
  }

  public Record<IncidentRecordValue> receiveFirstIncidentCommand(final IncidentIntent intent) {
    return receiveIncidents().withIntent(intent).onlyCommands().getFirst();
  }

  /////////////////////////////////////////////
  // DEPLOYMENTS //////////////////////////////
  /////////////////////////////////////////////

  public DeploymentRecordStream receiveDeployments() {
    return RecordingExporter.deploymentRecords().withPartitionId(partitionId);
  }

  public Record<DeploymentRecordValue> receiveFirstDeploymentEvent(
      final DeploymentIntent intent, final long deploymentKey) {
    return receiveDeployments().withIntent(intent).withKey(deploymentKey).getFirst();
  }

  /////////////////////////////////////////////
  // WORKFLOW INSTANCES ///////////////////////
  /////////////////////////////////////////////

  public WorkflowInstanceRecordStream receiveWorkflowInstances() {
    return RecordingExporter.workflowInstanceRecords().withPartitionId(partitionId);
  }

  public Record<WorkflowInstanceRecordValue> receiveFirstWorkflowInstanceCommand(
      final WorkflowInstanceIntent intent) {
    return receiveWorkflowInstances().withIntent(intent).onlyCommands().getFirst();
  }

  public Record<WorkflowInstanceRecordValue> receiveFirstWorkflowInstanceEvent(
      final WorkflowInstanceIntent intent) {
    return receiveWorkflowInstances().withIntent(intent).getFirst();
  }

  public Record<WorkflowInstanceRecordValue> receiveFirstWorkflowInstanceEvent(
      final long wfInstanceKey, final String elementId, final Intent intent) {
    return receiveWorkflowInstances()
        .withIntent(intent)
        .withWorkflowInstanceKey(wfInstanceKey)
        .withElementId(elementId)
        .getFirst();
  }

  public Record<WorkflowInstanceRecordValue> receiveFirstWorkflowInstanceEvent(
      final long wfInstanceKey, final Intent intent) {
    return receiveWorkflowInstances()
        .withIntent(intent)
        .withWorkflowInstanceKey(wfInstanceKey)
        .getFirst();
  }

  public Record<WorkflowInstanceRecordValue> receiveElementInState(
      final String elementId, final WorkflowInstanceIntent intent) {
    return receiveWorkflowInstances().withIntent(intent).withElementId(elementId).getFirst();
  }

  public Record<WorkflowInstanceRecordValue> receiveElementInState(
      final long workflowInstanceKey, final String elementId, final WorkflowInstanceIntent intent) {
    return receiveFirstWorkflowInstanceEvent(workflowInstanceKey, elementId, intent);
  }

  public List<Record<WorkflowInstanceRecordValue>> receiveElementInstancesInState(
      Intent intent, int expectedNumber) {
    return receiveWorkflowInstances()
        .withIntent(intent)
        .limit(expectedNumber)
        .collect(Collectors.toList());
  }

  /////////////////////////////////////////////
  // JOBS /////////////////////////////////////
  /////////////////////////////////////////////

  public JobRecordStream receiveJobs() {
    return RecordingExporter.jobRecords().withPartitionId(partitionId);
  }

  public Record<JobRecordValue> receiveFirstJobEvent(final JobIntent intent) {
    return receiveJobs().withIntent(intent).getFirst();
  }

  public Record<JobRecordValue> receiveFirstJobCommand(final JobIntent intent) {
    return receiveJobs().onlyCommands().withIntent(intent).getFirst();
  }

  /////////////////////////////////////////////
  // JOB BATCHS ///////////////////////////////
  /////////////////////////////////////////////

  public JobBatchRecordStream receiveJobBatchs() {
    return RecordingExporter.jobBatchRecords().withPartitionId(partitionId);
  }

  public JobBatchRecordStream receiveFirstJobBatchCommands() {
    return RecordingExporter.jobBatchRecords().withPartitionId(partitionId).onlyCommands();
  }

  /////////////////////////////////////////////
  // MESSAGE //////////////////////////////////
  /////////////////////////////////////////////

  public MessageRecordStream receiveMessages() {
    return RecordingExporter.messageRecords().withPartitionId(partitionId);
  }

  public Record<MessageRecordValue> receiveFirstMessageEvent(final MessageIntent intent) {
    return receiveMessages().withIntent(intent).getFirst();
  }

  /////////////////////////////////////////////
  // MESSAGE SUBSCRIPTIONS ////////////////////
  /////////////////////////////////////////////

  public MessageSubscriptionRecordStream receiveMessageSubscriptions() {
    return RecordingExporter.messageSubscriptionRecords().withPartitionId(partitionId);
  }

  /////////////////////////////////////////////
  // MESSAGE SUBSCRIPTIONS ////////////////////
  /////////////////////////////////////////////

  public WorkflowInstanceSubscriptionRecordStream receiveWorkflowInstanceSubscriptions() {
    return RecordingExporter.workflowInstanceSubscriptionRecords().withPartitionId(partitionId);
  }

  /////////////////////////////////////////////
  // TIMER ////////////////////////////////////
  /////////////////////////////////////////////

  public TimerRecordStream receiveTimerRecords() {
    return RecordingExporter.timerRecords().withPartitionId(partitionId);
  }

  public Record<TimerRecordValue> receiveTimerRecord(String handlerNodeId, TimerIntent intent) {
    return receiveTimerRecords().withIntent(intent).withHandlerNodeId(handlerNodeId).getFirst();
  }

  public Record<TimerRecordValue> receiveTimerRecord(
      DirectBuffer handlerNodeId, TimerIntent intent) {
    return receiveTimerRecord(bufferAsString(handlerNodeId), intent);
  }
}
