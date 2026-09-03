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
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryEmbeddedToolCall;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryMessageContent;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryContentType;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class AgentHistoryDiscardTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String SERVICE_TASK_ID = "agent-task";
  private static final String JOB_TYPE = "agentic-task";

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
    // Each createHistoryItem call is a separate AGENT_INSTANCE:UPDATE with its own
    // historyItemId, so they must not collapse into the same duplicate-detected item.
    assertThat(secondItemKey).isNotEqualTo(firstItemKey);

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
    final long lease2ItemKey =
        createHistoryItem(agentInstanceKey, jobKey, elementInstanceKey, "lease-2");
    // Guard against the lease-2 item silently collapsing into the lease-1 item: if it did, the
    // assertion below would pass vacuously with only one item ever having existed.
    assertThat(lease2ItemKey).isNotEqualTo(lease1ItemKey);
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

    final var item =
        new AgentHistoryRecord()
            .setHistoryItemId(Strings.newRandomValidBpmnId())
            .setRole(AgentHistoryRole.ASSISTANT)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("some large response text"))
            .addToolCall(
                new AgentHistoryEmbeddedToolCall()
                    .setToolCallId("call-1")
                    .setToolName("http-tool")
                    .setElementId("call-activity")
                    .setArguments(Map.of()));
    item.getMetrics().setInputTokens(100).setOutputTokens(50).setDurationMs(1234);

    ENGINE
        .agentInstances()
        .withAgentInstanceKey(agentInstanceKey)
        .withElementInstanceKey(elementInstanceKey)
        .withJobKey(jobKey)
        .withHistory(List.of(item))
        .update();

    final var discarded = ENGINE.agentHistories().withJobKey(jobKey).discard();

    // The item was created with content/toolCalls/metrics, but they are stripped at primary-storage
    // insert — the emitted DISCARDED event must carry none of them.
    assertThat(discarded.getValue().getContent()).isEmpty();
    assertThat(discarded.getValue().getToolCalls()).isEmpty();
    assertThat(discarded.getValue().getMetrics().getInputTokens()).isEqualTo(-1L);
    assertThat(discarded.getValue().getMetrics().getOutputTokens()).isEqualTo(-1L);
    assertThat(discarded.getValue().getMetrics().getDurationMs()).isEqualTo(-1L);
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
                .serviceTask(
                    SERVICE_TASK_ID, t -> t.zeebeJobType(JOB_TYPE).zeebeAiAgentTaskDefinition())
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
    return ENGINE.agentInstances().withElementInstanceKey(elementInstanceKey).create();
  }

  private static long createHistoryItem(
      final long agentInstanceKey,
      final long jobKey,
      final long elementInstanceKey,
      final String jobLease) {
    final var historyItemId = Strings.newRandomValidBpmnId();
    ENGINE
        .agentInstances()
        .withAgentInstanceKey(agentInstanceKey)
        .withElementInstanceKey(elementInstanceKey)
        .withJobKey(jobKey)
        .withJobLease(jobLease)
        .withHistory(
            List.of(
                new AgentHistoryRecord()
                    .setHistoryItemId(historyItemId)
                    .setRole(AgentHistoryRole.USER)
                    .setLoopIteration(1)
                    .addContent(
                        new AgentHistoryMessageContent()
                            .setContentType(AgentHistoryContentType.TEXT)
                            .setText("hi"))))
        .update();
    return RecordingExporter.agentHistoryRecords(AgentHistoryIntent.CREATED)
        .withAgentInstanceKey(agentInstanceKey)
        .filter(r -> r.getValue().getHistoryItemId().equals(historyItemId))
        .getFirst()
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
