/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agenthistory;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class AgentHistoryCreateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String SERVICE_TASK_ID = "agent-task";
  private static final String JOB_TYPE = JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX;

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldEmitCreatedEventOnHappyPath() {
    final var serviceTaskInstance = deployAndCreateProcessInstance();
    final var elementInstanceKey = serviceTaskInstance.getKey();
    final var processInstanceKey = serviceTaskInstance.getValue().getProcessInstanceKey();

    final var agentInstanceRecord = createAgentInstance(elementInstanceKey);
    final var agentInstanceKey = agentInstanceRecord.getKey();

    final var jobKey = activateJobForProcessInstance(processInstanceKey);

    final Record<AgentHistoryRecordValue> created =
        ENGINE
            .agentHistories()
            .withAgentInstanceKey(agentInstanceKey)
            .withJobKey(jobKey)
            .withElementInstanceKey(elementInstanceKey)
            .withRole(AgentHistoryRole.USER)
            .create();

    assertThat(created.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(created.getIntent()).isEqualTo(AgentHistoryIntent.CREATED);
    assertThat(created.getValue().getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(created.getValue().getAgentInstanceKey()).isEqualTo(agentInstanceKey);
    assertThat(created.getValue().getJobKey()).isEqualTo(jobKey);
    assertThat(created.getValue().getElementInstanceKey()).isEqualTo(elementInstanceKey);
    assertThat(created.getValue().getBpmnProcessId()).isEqualTo(PROCESS_ID);
  }

  @Test
  public void shouldRejectWhenAgentInstanceNotFound() {
    final var serviceTaskInstance = deployAndCreateProcessInstance();
    final var elementInstanceKey = serviceTaskInstance.getKey();
    final var processInstanceKey = serviceTaskInstance.getValue().getProcessInstanceKey();

    final var jobKey = activateJobForProcessInstance(processInstanceKey);

    final Record<AgentHistoryRecordValue> rejection =
        ENGINE
            .agentHistories()
            .withAgentInstanceKey(999999999L)
            .withJobKey(jobKey)
            .withElementInstanceKey(elementInstanceKey)
            .expectRejection()
            .create();

    assertThat(rejection.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejection.getRejectionReason()).contains("999999999");
  }

  @Test
  public void shouldRejectWhenJobNotActivated() {
    final var serviceTaskInstance = deployAndCreateProcessInstance();
    final var elementInstanceKey = serviceTaskInstance.getKey();

    final var agentInstanceRecord = createAgentInstance(elementInstanceKey);
    final var agentInstanceKey = agentInstanceRecord.getKey();

    final Record<AgentHistoryRecordValue> rejection =
        ENGINE
            .agentHistories()
            .withAgentInstanceKey(agentInstanceKey)
            .withJobKey(888888888L)
            .withElementInstanceKey(elementInstanceKey)
            .expectRejection()
            .create();

    assertThat(rejection.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejection.getRejectionReason()).contains("888888888");
  }

  @Test
  public void shouldRejectWhenElementInstanceKeyMismatch() {
    final var serviceTaskInstance = deployAndCreateProcessInstance();
    final var elementInstanceKey = serviceTaskInstance.getKey();
    final var processInstanceKey = serviceTaskInstance.getValue().getProcessInstanceKey();

    final var agentInstanceRecord = createAgentInstance(elementInstanceKey);
    final var agentInstanceKey = agentInstanceRecord.getKey();

    final var jobKey = activateJobForProcessInstance(processInstanceKey);

    final Record<AgentHistoryRecordValue> rejection =
        ENGINE
            .agentHistories()
            .withAgentInstanceKey(agentInstanceKey)
            .withJobKey(jobKey)
            .withElementInstanceKey(elementInstanceKey + 9999L)
            .expectRejection()
            .create();

    assertThat(rejection.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldRejectWhenElementInstanceNotInAgentInstance() {
    // Two separate process instances; an agent instance is created only for the first.
    // The history command supplies the second job (whose elementInstanceKey is the second element
    // instance) together with the first agent instance — the element instance is not in the agent
    // instance's list, so the engine must reject with NOT_FOUND.
    final var firstServiceTask = deployAndCreateProcessInstance();
    final var firstElementInstanceKey = firstServiceTask.getKey();
    final var firstProcessInstanceKey = firstServiceTask.getValue().getProcessInstanceKey();

    // Activate the first job so it is no longer available for subsequent activate calls.
    activateJobForProcessInstance(firstProcessInstanceKey);

    final var secondProcessInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var secondServiceTask =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(secondProcessInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SERVICE_TASK_ID)
            .getFirst();
    final var secondElementInstanceKey = secondServiceTask.getKey();

    final var agentInstanceRecord = createAgentInstance(firstElementInstanceKey);
    final var agentInstanceKey = agentInstanceRecord.getKey();

    final var secondJobKey = activateJobForProcessInstance(secondProcessInstanceKey);

    final Record<AgentHistoryRecordValue> rejection =
        ENGINE
            .agentHistories()
            .withAgentInstanceKey(agentInstanceKey)
            .withJobKey(secondJobKey)
            .withElementInstanceKey(secondElementInstanceKey)
            .expectRejection()
            .create();

    assertThat(rejection.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejection.getRejectionReason()).contains(String.valueOf(secondElementInstanceKey));
  }

  @Test
  public void shouldSucceedWhenJobHasNoLease() {
    // given — job activated without withLease(), so leaseToken is empty
    final var serviceTaskInstance = deployAndCreateProcessInstance();
    final var elementInstanceKey = serviceTaskInstance.getKey();
    final var processInstanceKey = serviceTaskInstance.getValue().getProcessInstanceKey();

    final var agentInstanceKey = createAgentInstance(elementInstanceKey).getKey();
    final var jobKey = activateJobForProcessInstance(processInstanceKey);

    // when
    final Record<AgentHistoryRecordValue> created =
        ENGINE
            .agentHistories()
            .withAgentInstanceKey(agentInstanceKey)
            .withJobKey(jobKey)
            .withElementInstanceKey(elementInstanceKey)
            .withRole(AgentHistoryRole.USER)
            .withJobLease("")
            .create();

    // then — lease check is skipped and history record was created
    assertThat(created.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(created.getIntent()).isEqualTo(AgentHistoryIntent.CREATED);
  }

  @Test
  public void shouldRejectWhenJobLeaseMismatch() {
    // given — job activated with withLease(), but CREATE carries a stale/wrong lease string
    final var serviceTaskInstance = deployAndCreateProcessInstance();
    final var elementInstanceKey = serviceTaskInstance.getKey();
    final var processInstanceKey = serviceTaskInstance.getValue().getProcessInstanceKey();

    final var agentInstanceKey = createAgentInstance(elementInstanceKey).getKey();
    final var job = activateJobForProcessInstanceWithLease(processInstanceKey);

    // when
    final Record<AgentHistoryRecordValue> rejection =
        ENGINE
            .agentHistories()
            .withAgentInstanceKey(agentInstanceKey)
            .withJobKey(job.key())
            .withElementInstanceKey(elementInstanceKey)
            .withRole(AgentHistoryRole.USER)
            .withJobLease("stale-lease-that-does-not-match")
            .expectRejection()
            .create();

    // then
    assertThat(rejection.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejection.getRejectionReason())
        .isEqualTo(
            "Expected to create agent history entry for job with key '%d', but the supplied lease does not match. The job may have been re-activated."
                .formatted(job.key()));
  }

  @Test
  public void shouldRejectWhenJobHasLeaseButCommandOmitsIt() {
    // given — job activated with withLease(), but CREATE carries no lease (empty string)
    final var serviceTaskInstance = deployAndCreateProcessInstance();
    final var elementInstanceKey = serviceTaskInstance.getKey();
    final var processInstanceKey = serviceTaskInstance.getValue().getProcessInstanceKey();

    final var agentInstanceKey = createAgentInstance(elementInstanceKey).getKey();
    final var job = activateJobForProcessInstanceWithLease(processInstanceKey);

    // when — empty string does not equal the stored token, so the lease check fires
    final Record<AgentHistoryRecordValue> rejection =
        ENGINE
            .agentHistories()
            .withAgentInstanceKey(agentInstanceKey)
            .withJobKey(job.key())
            .withElementInstanceKey(elementInstanceKey)
            .withRole(AgentHistoryRole.USER)
            .expectRejection()
            .create();

    // then
    assertThat(rejection.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejection.getRejectionReason())
        .isEqualTo(
            "Expected to create agent history entry for job with key '%d', but the supplied lease does not match. The job may have been re-activated."
                .formatted(job.key()));
  }

  // --- helpers ---

  private static Record<ProcessInstanceRecordValue> deployAndCreateProcessInstance() {
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType(JOB_TYPE))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .withElementId(SERVICE_TASK_ID)
        .getFirst();
  }

  private static long activateJobForProcessInstance(final long processInstanceKey) {
    ENGINE.jobs().withType(JOB_TYPE).activate();
    return RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(JOB_TYPE)
        .getFirst()
        .getKey();
  }

  private static Record<?> createAgentInstance(final long elementInstanceKey) {
    return ENGINE
        .agentInstances()
        .withElementInstanceKey(elementInstanceKey)
        .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
        .create();
  }

  private static ActivatedJob activateJobForProcessInstanceWithLease(
      final long processInstanceKey) {
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(JOB_TYPE)
            .getFirst()
            .getKey();
    final var batchRecord = ENGINE.jobs().withType(JOB_TYPE).withLease().activate();
    final int jobIndex = batchRecord.getValue().getJobKeys().indexOf(jobKey);
    assertThat(jobIndex)
        .as("expected activated job batch to contain job key %d", jobKey)
        .isGreaterThanOrEqualTo(0);
    final String leaseToken = batchRecord.getValue().getJobs().get(jobIndex).getLeaseToken();
    return new ActivatedJob(jobKey, leaseToken);
  }

  private record ActivatedJob(long key, String leaseToken) {}
}
