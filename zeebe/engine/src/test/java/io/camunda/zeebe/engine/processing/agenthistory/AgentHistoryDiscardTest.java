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
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
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

public class AgentHistoryDiscardTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String SERVICE_TASK_ID = "agent-task";
  private static final String JOB_TYPE = JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX;

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldDiscardAllItemsForJobKeyOnEmptyLease() {
    final var serviceTaskInstance = deployAndCreateProcessInstance();
    final var elementInstanceKey = serviceTaskInstance.getKey();
    final var processInstanceKey = serviceTaskInstance.getValue().getProcessInstanceKey();
    final var agentInstanceKey = createAgentInstance(elementInstanceKey).getKey();
    final var jobKey = activateJobForProcessInstance(processInstanceKey);

    // Two items share jobKey but have different leases — DISCARD with no lease must discard both
    // regardless of lease.
    final long firstItemKey =
        createHistoryItem(agentInstanceKey, jobKey, elementInstanceKey, "lease-a");
    final long secondItemKey =
        createHistoryItem(agentInstanceKey, jobKey, elementInstanceKey, "lease-b");

    // An item on an unrelated job must not be discarded.
    createUnrelatedJobHistoryItem("");

    final var firstDiscarded = ENGINE.agentHistories().withJobKey(jobKey).discard();
    final long discardPosition = firstDiscarded.getSourceRecordPosition();
    final long clockResetKey = ENGINE.clock().reset().getKey();

    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getKey() == clockResetKey)
                .withValueType(ValueType.AGENT_HISTORY)
                .withIntent(AgentHistoryIntent.DISCARDED)
                .filter(r -> r.getSourceRecordPosition() == discardPosition)
                .map(Record::getKey))
        .containsExactlyInAnyOrder(firstItemKey, secondItemKey);
  }

  @Test
  public void shouldDiscardOnlyMatchingLeaseOnLeaseBasedDiscard() {
    final var serviceTaskInstance = deployAndCreateProcessInstance();
    final var elementInstanceKey = serviceTaskInstance.getKey();
    final var processInstanceKey = serviceTaskInstance.getValue().getProcessInstanceKey();
    final var agentInstanceKey = createAgentInstance(elementInstanceKey).getKey();
    final var jobKey = activateJobForProcessInstance(processInstanceKey);

    final long lease1ItemKey =
        createHistoryItem(agentInstanceKey, jobKey, elementInstanceKey, "lease-1");
    createHistoryItem(agentInstanceKey, jobKey, elementInstanceKey, "lease-2");
    createUnrelatedJobHistoryItem("lease-1");

    final var firstDiscarded =
        ENGINE.agentHistories().withJobKey(jobKey).withJobLease("lease-1").discard();
    final long discardPosition = firstDiscarded.getSourceRecordPosition();
    final long clockResetKey = ENGINE.clock().reset().getKey();

    // Only the matching-lease item is discarded; the superseding lease-2 item and the unrelated
    // job's item are left untouched.
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getKey() == clockResetKey)
                .withValueType(ValueType.AGENT_HISTORY)
                .withIntent(AgentHistoryIntent.DISCARDED)
                .filter(r -> r.getSourceRecordPosition() == discardPosition)
                .map(Record::getKey))
        .containsExactly(lease1ItemKey);
  }

  @Test
  public void shouldEmitDiscardedEventCarryingIdentityFieldsOnly() {
    final var serviceTaskInstance = deployAndCreateProcessInstance();
    final var elementInstanceKey = serviceTaskInstance.getKey();
    final var processInstanceKey = serviceTaskInstance.getValue().getProcessInstanceKey();
    final var agentInstanceKey = createAgentInstance(elementInstanceKey).getKey();
    final var jobKey = activateJobForProcessInstance(processInstanceKey);
    final long itemKey = createHistoryItem(agentInstanceKey, jobKey, elementInstanceKey, "");

    final var discarded = ENGINE.agentHistories().withJobKey(jobKey).discard();

    assertThat(discarded.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(discarded.getKey()).isEqualTo(itemKey);
    assertThat(discarded.getValue().getJobKey()).isEqualTo(jobKey);
    assertThat(discarded.getValue().getAgentInstanceKey()).isEqualTo(agentInstanceKey);
    assertThat(discarded.getValue().getElementInstanceKey()).isEqualTo(elementInstanceKey);
  }

  @Test
  public void shouldStripContentToolCallsAndMetricsFromDiscardedEvent() {
    final var serviceTaskInstance = deployAndCreateProcessInstance();
    final var elementInstanceKey = serviceTaskInstance.getKey();
    final var processInstanceKey = serviceTaskInstance.getValue().getProcessInstanceKey();
    final var agentInstanceKey = createAgentInstance(elementInstanceKey).getKey();
    final var jobKey = activateJobForProcessInstance(processInstanceKey);

    ENGINE
        .agentHistories()
        .withAgentInstanceKey(agentInstanceKey)
        .withJobKey(jobKey)
        .withElementInstanceKey(elementInstanceKey)
        .withRole(AgentHistoryRole.ASSISTANT)
        .withTextContent("some large response text")
        .withToolCall("call-1", "http-tool", "call-activity")
        .withMetrics(100, 50, 1234)
        .create();

    final var discarded = ENGINE.agentHistories().withJobKey(jobKey).discard();

    // The item was created with content/toolCalls/metrics, but they are stripped at primary-storage
    // insert — the emitted DISCARDED event must carry none of them.
    assertThat(discarded.getValue().getContent()).isEmpty();
    assertThat(discarded.getValue().getToolCalls()).isEmpty();
    assertThat(discarded.getValue().getMetrics().getInputTokens()).isZero();
    assertThat(discarded.getValue().getMetrics().getOutputTokens()).isZero();
    assertThat(discarded.getValue().getMetrics().getDurationMs()).isZero();
  }

  @Test
  public void shouldNotEmitAnyEventWhenNoItemsExistForJobKey() {
    final var serviceTaskInstance = deployAndCreateProcessInstance();
    final var processInstanceKey = serviceTaskInstance.getValue().getProcessInstanceKey();
    final var jobKey = activateJobForProcessInstance(processInstanceKey);

    // No CREATED items for the job — DISCARD must be a no-op. The client helper would block
    // waiting for a follow-up event, which a no-op never produces, so write the command directly.
    ENGINE.writeRecords(
        RecordToWrite.command()
            .key(jobKey)
            .agentHistory(AgentHistoryIntent.DISCARD, new AgentHistoryRecord().setJobKey(jobKey)));
    final long clockResetKey = ENGINE.clock().reset().getKey();

    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getKey() == clockResetKey)
                .withValueType(ValueType.AGENT_HISTORY)
                .withIntent(AgentHistoryIntent.DISCARDED)
                .exists())
        .isFalse();
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
