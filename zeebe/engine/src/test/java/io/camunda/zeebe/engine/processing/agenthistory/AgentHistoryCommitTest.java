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
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class AgentHistoryCommitTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String SERVICE_TASK_ID = "agent-task";
  private static final String JOB_TYPE = JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX;

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldEmitCommittedEventOnCommitCommand() {
    final var serviceTaskInstance = deployAndCreateProcessInstance();
    final var elementInstanceKey = serviceTaskInstance.getKey();
    final var processInstanceKey = serviceTaskInstance.getValue().getProcessInstanceKey();

    final var agentInstanceKey = createAgentInstance(elementInstanceKey).getKey();
    final var jobKey = activateJobForProcessInstance(processInstanceKey);
    final var itemKey = createHistoryItem(agentInstanceKey, jobKey, elementInstanceKey, "");

    final var committed = ENGINE.agentHistories().withJobKey(jobKey).commit();

    // AgentHistoryCommitProcessor emits COMMITTED carrying the full stored record
    assertThat(committed.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(committed.getKey()).isEqualTo(itemKey);
    assertThat(committed.getValue().getJobKey()).isEqualTo(jobKey);
    assertThat(committed.getValue().getAgentInstanceKey()).isEqualTo(agentInstanceKey);
    assertThat(committed.getValue().getElementInstanceKey()).isEqualTo(elementInstanceKey);
  }

  @Test
  public void shouldEmitCommittedForAllItemsWithSameJobKey() {
    final var serviceTaskInstance = deployAndCreateProcessInstance();
    final var elementInstanceKey = serviceTaskInstance.getKey();
    final var processInstanceKey = serviceTaskInstance.getValue().getProcessInstanceKey();
    final var agentInstanceKey = createAgentInstance(elementInstanceKey).getKey();
    final var jobKey = activateJobForProcessInstance(processInstanceKey);

    // Two items share jobKey but have different leases — COMMIT with no lease must commit both
    // regardless of lease.
    final long firstItemKey =
        createHistoryItem(agentInstanceKey, jobKey, elementInstanceKey, "lease-a");
    final long secondItemKey =
        createHistoryItem(agentInstanceKey, jobKey, elementInstanceKey, "lease-b");

    // An item on an unrelated job must not be committed.
    createUnrelatedJobHistoryItem("lease-a");

    final var firstCommitted = ENGINE.agentHistories().withJobKey(jobKey).commit();
    final long commitPosition = firstCommitted.getSourceRecordPosition();
    final long clockResetKey = ENGINE.clock().reset().getKey();

    // COMMIT with no lease → visitByJobKey commits all items for jobKey regardless of lease.
    // withSourceRecordPosition scopes to events from this COMMIT command only; the unrelated job's
    // item is not visited by visitByJobKey(jobKey) so no event for it can appear.
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getKey() == clockResetKey)
                .withValueType(ValueType.AGENT_HISTORY)
                .withIntent(AgentHistoryIntent.COMMITTED)
                .filter(r -> r.getSourceRecordPosition() == commitPosition)
                .map(Record::getKey))
        .containsExactlyInAnyOrder(firstItemKey, secondItemKey);
  }

  @Test
  public void shouldCommitMatchingLeaseAndDiscardSupersededOnLeaseBasedCommit() {
    final var serviceTaskInstance = deployAndCreateProcessInstance();
    final var elementInstanceKey = serviceTaskInstance.getKey();
    final var processInstanceKey = serviceTaskInstance.getValue().getProcessInstanceKey();
    final var agentInstanceKey = createAgentInstance(elementInstanceKey).getKey();
    final var jobKey = activateJobForProcessInstance(processInstanceKey);

    // lease-1 and lease-2 items share jobKey; the unrelated job's item also carries lease-1 — it
    // must not be affected by the COMMIT, proving the filter is scoped to jobKey.
    final long lease1ItemKey =
        createHistoryItem(agentInstanceKey, jobKey, elementInstanceKey, "lease-1");
    final long lease2ItemKey =
        createHistoryItem(agentInstanceKey, jobKey, elementInstanceKey, "lease-2");
    createUnrelatedJobHistoryItem("lease-1");

    final var firstCommitted =
        ENGINE.agentHistories().withJobKey(jobKey).withJobLease("lease-1").commit();
    final long commitPosition = firstCommitted.getSourceRecordPosition();
    final long clockResetKey = ENGINE.clock().reset().getKey();

    // visitByJobLease(lease-1) → lease-1 item COMMITTED
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getKey() == clockResetKey)
                .withValueType(ValueType.AGENT_HISTORY)
                .withIntent(AgentHistoryIntent.COMMITTED)
                .filter(r -> r.getSourceRecordPosition() == commitPosition)
                .map(Record::getKey))
        .containsExactly(lease1ItemKey);

    // discard pass (visitByJobKey) → lease-2 item DISCARDED as superseded activation
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getKey() == clockResetKey)
                .withValueType(ValueType.AGENT_HISTORY)
                .withIntent(AgentHistoryIntent.DISCARDED)
                .filter(r -> r.getSourceRecordPosition() == commitPosition)
                .map(Record::getKey))
        .containsExactly(lease2ItemKey);
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

  private static long createHistoryItem(
      final long agentInstanceKey,
      final long jobKey,
      final long elementInstanceKey,
      final String jobLease) {
    return ENGINE
        .agentHistories()
        .withAgentInstanceKey(agentInstanceKey)
        .withJobKey(jobKey)
        .withElementInstanceKey(elementInstanceKey)
        .withJobLease(jobLease)
        .withRole(AgentHistoryRole.USER)
        .create()
        .getKey();
  }

  /**
   * Creates a second, unrelated agentic job (a new process instance of the already-deployed
   * process) with its own agent instance, and a single history item on it with the given lease.
   * Used as a control case to prove an operation scoped to one job does not affect another.
   */
  private static long createUnrelatedJobHistoryItem(final String jobLease) {
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SERVICE_TASK_ID)
            .getFirst();
    final long elementInstanceKey = serviceTaskInstance.getKey();

    final long agentInstanceKey = createAgentInstance(elementInstanceKey).getKey();
    final long jobKey = activateJobForProcessInstance(processInstanceKey);

    return createHistoryItem(agentInstanceKey, jobKey, elementInstanceKey, jobLease);
  }
}
