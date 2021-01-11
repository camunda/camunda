/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.broker.protocol.commandapi;

import static io.zeebe.protocol.record.intent.JobIntent.ACTIVATED;
import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.Workflow;
import io.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.protocol.record.intent.JobBatchIntent;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.MessageIntent;
import io.zeebe.protocol.record.intent.TimerIntent;
import io.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceCreationIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.DeploymentRecordValue;
import io.zeebe.protocol.record.value.IncidentRecordValue;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.protocol.record.value.MessageRecordValue;
import io.zeebe.protocol.record.value.TimerRecordValue;
import io.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.TestUtil;
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
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.collection.Tuple;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

public final class PartitionTestClient {
  // workflow related properties

  public static final String PROP_WORKFLOW_RESOURCES = "resources";
  public static final String PROP_WORKFLOW_VERSION = "version";
  public static final String PROP_WORKFLOW_VARIABLES = "variable";
  public static final String PROP_WORKFLOW_INSTANCE_KEY = "workflowInstanceKey";
  public static final String PROP_WORKFLOW_KEY = "workflowKey";

  private final CommandApiRule apiRule;
  private final int partitionId;

  public PartitionTestClient(final CommandApiRule apiRule, final int partitionId) {
    this.apiRule = apiRule;
    this.partitionId = partitionId;
  }

  public long deploy(final BpmnModelInstance workflow) {
    final ExecuteCommandResponse response = deployWithResponse(workflow);

    assertThat(response.getRecordType())
        .withFailMessage("Deployment failed: %s", response.getRejectionReason())
        .isEqualTo(RecordType.EVENT);

    final long key = response.getKey();

    TestUtil.waitUntil(
        () ->
            RecordingExporter.deploymentRecords(DeploymentIntent.DISTRIBUTED)
                .withRecordKey(key)
                .exists());

    return key;
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
    deploymentResource.put("resourceName", resourceName);

    final ExecuteCommandResponse commandResponse =
        apiRule
            .createCmdRequest()
            .partitionId(Protocol.DEPLOYMENT_PARTITION)
            .type(ValueType.DEPLOYMENT, DeploymentIntent.CREATE)
            .command()
            .put(PROP_WORKFLOW_RESOURCES, Collections.singletonList(deploymentResource))
            .done()
            .sendAndAwait();

    return commandResponse;
  }

  public Workflow deployWorkflow(final BpmnModelInstance workflow) {
    final DeploymentRecord request = new DeploymentRecord();
    final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(outStream, workflow);

    request.resources().add().setResource(outStream.toByteArray()).setResourceName("process.bpmn");

    final DeploymentRecord response = deploy(request);
    final Iterator<Workflow> iterator = response.workflows().iterator();
    assertThat(iterator).as("Expected at least one deployed workflow, but none returned").hasNext();

    return iterator.next();
  }

  public DeploymentRecord deploy(final Function<DeploymentRecord, DeploymentRecord> transformer) {
    return deploy(transformer.apply(new DeploymentRecord()));
  }

  public DeploymentRecord deploy(final DeploymentRecord request) {
    final ExecuteCommandResponse response =
        executeCommandRequest(ValueType.DEPLOYMENT, DeploymentIntent.CREATE, request);

    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(DeploymentIntent.CREATED);

    return response.readInto(new DeploymentRecord());
  }

  public WorkflowInstanceCreationRecord createWorkflowInstance(
      final Function<WorkflowInstanceCreationRecord, WorkflowInstanceCreationRecord> mapper) {
    return createWorkflowInstance(mapper.apply(new WorkflowInstanceCreationRecord()));
  }

  public WorkflowInstanceCreationRecord createWorkflowInstance(
      final WorkflowInstanceCreationRecord record) {
    final ExecuteCommandResponse response =
        executeCommandRequest(
            ValueType.WORKFLOW_INSTANCE_CREATION, WorkflowInstanceCreationIntent.CREATE, record);

    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(WorkflowInstanceCreationIntent.CREATED);

    return response.readInto(new WorkflowInstanceCreationRecord());
  }

  public ExecuteCommandResponse executeCommandRequest(
      final ValueType valueType, final Intent intent, final BufferWriter command) {
    return executeCommandRequest(valueType, intent, command, -1);
  }

  public ExecuteCommandResponse executeCommandRequest(
      final ValueType valueType, final Intent intent, final BufferWriter command, final long key) {
    return apiRule
        .createCmdRequest()
        .partitionId(partitionId)
        .key(key)
        .type(valueType, intent)
        .command(command)
        .sendAndAwait();
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

  public void updateVariables(final long scopeKey, final Map<String, Object> document) {
    updateVariables(scopeKey, VariableDocumentUpdateSemantic.PROPAGATE, document);
  }

  public void updateVariables(
      final long scopeKey,
      final VariableDocumentUpdateSemantic updateSemantics,
      final Map<String, Object> document) {
    final ExecuteCommandResponse response =
        apiRule
            .createCmdRequest()
            .type(ValueType.VARIABLE_DOCUMENT, VariableDocumentIntent.UPDATE)
            .command()
            .put("scopeKey", scopeKey)
            .put("updateSemantics", updateSemantics)
            .put("document", MsgPackUtil.asMsgPack(document).byteArray())
            .done()
            .sendAndAwait();

    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(VariableDocumentIntent.UPDATED);
  }

  public long createJob(final String type) {
    return createJob(type, b -> {}, "{}");
  }

  public long createJob(
      final String type, final Consumer<ServiceTaskBuilder> consumer, final String variables) {
    deploy(
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                b -> {
                  b.zeebeJobType(type).zeebeJobRetries("3");
                  consumer.accept(b);
                })
            .done());

    final long workflowInstance =
        createWorkflowInstance(
                r -> r.setBpmnProcessId("process").setVariables(MsgPackUtil.asMsgPack(variables)))
            .getWorkflowInstanceKey();

    return RecordingExporter.jobRecords(JobIntent.CREATED)
        .withType(type)
        .filter(j -> j.getValue().getWorkflowInstanceKey() == workflowInstance)
        .getFirst()
        .getKey();
  }

  public JobRecord activateAndCompleteFirstJob(
      final String jobType, final Predicate<JobRecord> filter) {
    final Tuple<Long, JobRecord> pair = activateJob(jobType, filter);
    return completeJob(pair.getLeft(), pair.getRight());
  }

  public Tuple<Long, JobRecord> activateJob(
      final String jobType, final Predicate<JobRecord> filter) {
    final JobBatchRecord request =
        new JobBatchRecord()
            .setType(jobType)
            .setMaxJobsToActivate(1)
            .setTimeout(1000)
            .setWorker("partition-" + partitionId + "-" + jobType);

    return doRepeatedly(
            () -> {
              final JobBatchRecord response = activateJobBatch(request);
              if (response.getMaxJobsToActivate() > 0) {
                final JobRecord job = response.jobs().iterator().next();
                if (filter.test(job)) {
                  return new Tuple<>(response.jobKeys().iterator().next().getValue(), job);
                }
              }

              return null;
            })
        .until(Objects::nonNull);
  }

  public JobRecord completeJob(
      final long jobKey, final Function<JobRecord, JobRecord> transformer) {
    return completeJob(jobKey, transformer.apply(new JobRecord()));
  }

  public JobRecord completeJob(final long jobKey, final JobRecord request) {
    final ExecuteCommandResponse response =
        executeCommandRequest(ValueType.JOB, JobIntent.COMPLETE, request, jobKey);

    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(JobIntent.COMPLETED);
    return response.readInto(new JobRecord());
  }

  public JobBatchRecord activateJobBatch(
      final Function<JobBatchRecord, JobBatchRecord> transformer) {
    return activateJobBatch(transformer.apply(new JobBatchRecord()));
  }

  public JobBatchRecord activateJobBatch(final JobBatchRecord request) {
    final ExecuteCommandResponse response =
        executeCommandRequest(ValueType.JOB_BATCH, JobBatchIntent.ACTIVATE, request);

    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(JobBatchIntent.ACTIVATED);
    return response.readInto(new JobBatchRecord());
  }

  public void completeJobOfType(final long workflowInstanceKey, final String jobType) {
    completeJob(
        jobType,
        MsgPackUtil.asMsgPackReturnArray("{}"),
        r -> r.getValue().getWorkflowInstanceKey() == workflowInstanceKey);
  }

  public void completeJobOfType(final String jobType) {
    completeJobOfType(jobType, "{}");
  }

  public void completeJobOfType(final String jobType, final byte[] variables) {
    completeJob(jobType, variables, e -> true);
  }

  public void completeJobOfType(final String jobType, final String jsonVariables) {
    completeJob(jobType, MsgPackUtil.asMsgPackReturnArray(jsonVariables), e -> true);
  }

  public ExecuteCommandResponse completeJob(final long key, final String variables) {
    return completeJob(key, MsgPackUtil.asMsgPackReturnArray(variables));
  }

  public ExecuteCommandResponse completeJob(final long key, final byte[] variables) {
    return apiRule
        .createCmdRequest()
        .type(ValueType.JOB, JobIntent.COMPLETE)
        .key(key)
        .command()
        .put("variables", variables)
        .done()
        .sendAndAwait();
  }

  public void completeJob(
      final String jobType,
      final byte[] variables,
      final Predicate<Record<JobRecordValue>> jobEventFilter) {
    apiRule.activateJobs(partitionId, jobType, 1000L).await();

    final Record<JobRecordValue> jobEvent =
        receiveJobs()
            .withIntent(ACTIVATED)
            .withType(jobType)
            .filter(jobEventFilter)
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected job locked event but not found."));

    final ExecuteCommandResponse response = completeJob(jobEvent.getKey(), variables);

    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(JobIntent.COMPLETED);
  }

  public ExecuteCommandResponse failJob(final long key, final int retries) {
    return apiRule
        .createCmdRequest()
        .type(ValueType.JOB, JobIntent.FAIL)
        .key(key)
        .command()
        .put("retries", retries)
        .done()
        .sendAndAwait();
  }

  public ExecuteCommandResponse failJobWithMessage(
      final long key, final int retries, final String errorMessage) {
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
      final long key, final String errorMessage) {
    return failJobWithMessage(key, 0, errorMessage);
  }

  public ExecuteCommandResponse updateJobRetries(final long key, final int retries) {
    return apiRule
        .createCmdRequest()
        .type(ValueType.JOB, JobIntent.UPDATE_RETRIES)
        .key(key)
        .command()
        .put("retries", retries)
        .done()
        .sendAndAwait();
  }

  public MessageRecord publishMessage(final Function<MessageRecord, MessageRecord> transformer) {
    return publishMessage(transformer.apply(new MessageRecord()));
  }

  public MessageRecord publishMessage(final MessageRecord request) {
    final ExecuteCommandResponse response =
        executeCommandRequest(ValueType.MESSAGE, MessageIntent.PUBLISH, request);

    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(MessageIntent.PUBLISHED);
    return response.readInto(new MessageRecord());
  }

  public ExecuteCommandResponse publishMessage(
      final String messageName, final String correlationKey) {
    return publishMessage(messageName, correlationKey, new byte[0]);
  }

  public ExecuteCommandResponse publishMessage(
      final String messageName, final String correlationKey, final DirectBuffer variables) {
    return publishMessage(messageName, correlationKey, BufferUtil.bufferAsArray(variables));
  }

  public ExecuteCommandResponse publishMessage(
      final String messageName, final String correlationKey, final String variables) {
    return publishMessage(messageName, correlationKey, MsgPackUtil.asMsgPackReturnArray(variables));
  }

  public ExecuteCommandResponse publishMessage(
      final String messageName,
      final String correlationKey,
      final DirectBuffer variables,
      final long ttl) {
    return publishMessage(messageName, correlationKey, BufferUtil.bufferAsArray(variables), ttl);
  }

  public ExecuteCommandResponse publishMessage(
      final String messageName, final String correlationKey, final byte[] variables) {
    return publishMessage(messageName, correlationKey, variables, Duration.ofHours(1).toMillis());
  }

  public ExecuteCommandResponse publishMessage(
      final String messageName,
      final String correlationKey,
      final byte[] variables,
      final long ttl) {
    return apiRule
        .createCmdRequest()
        .partitionId(partitionId)
        .type(ValueType.MESSAGE, MessageIntent.PUBLISH)
        .command()
        .put("name", messageName)
        .put("correlationKey", correlationKey)
        .put("timeToLive", ttl)
        .put("variables", variables)
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

  public ExecuteCommandResponse resolveIncident(final long incidentKey) {

    return apiRule
        .createCmdRequest()
        .partitionId(partitionId)
        .type(ValueType.INCIDENT, IncidentIntent.RESOLVE)
        .key(incidentKey)
        .command()
        .done()
        .sendAndAwait();
  }

  /////////////////////////////////////////////
  // DEPLOYMENTS //////////////////////////////
  /////////////////////////////////////////////

  public DeploymentRecordStream receiveDeployments() {
    return RecordingExporter.deploymentRecords().withPartitionId(partitionId);
  }

  public Record<DeploymentRecordValue> receiveFirstDeploymentEvent(
      final DeploymentIntent intent, final long deploymentKey) {
    return receiveDeployments().withIntent(intent).withRecordKey(deploymentKey).getFirst();
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
      final WorkflowInstanceIntent intent, final BpmnElementType elementType) {
    return receiveWorkflowInstances().withIntent(intent).withElementType(elementType).getFirst();
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

  public Record<WorkflowInstanceRecordValue> receiveFirstWorkflowInstanceEvent(
      final long wfInstanceKey, final Intent intent, final BpmnElementType elementType) {
    return receiveWorkflowInstances()
        .withIntent(intent)
        .withWorkflowInstanceKey(wfInstanceKey)
        .withElementType(elementType)
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
      final Intent intent, final int expectedNumber) {
    return receiveWorkflowInstances()
        .withIntent(intent)
        .limit(expectedNumber)
        .collect(Collectors.toList());
  }

  public List<Record<WorkflowInstanceRecordValue>> receiveElementInstancesInState(
      final Intent intent, final BpmnElementType elementType, final int expectedNumber) {
    return receiveWorkflowInstances()
        .withIntent(intent)
        .withElementType(elementType)
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

  public Record<TimerRecordValue> receiveTimerRecord(
      final String handlerNodeId, final TimerIntent intent) {
    return receiveTimerRecords().withIntent(intent).withHandlerNodeId(handlerNodeId).getFirst();
  }

  public Record<TimerRecordValue> receiveTimerRecord(
      final DirectBuffer handlerNodeId, final TimerIntent intent) {
    return receiveTimerRecord(bufferAsString(handlerNodeId), intent);
  }
}
