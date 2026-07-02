/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agenthistory;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
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

public class AgentHistoryCommitTest {

  // Tests use the stop/replay pattern to inject CREATED events directly rather than going through
  // AgentHistoryCreateProcessor. The CREATE command currently emits a COMMIT follow-up command
  // (TODO #55033) — using it here would trigger COMMIT inline in the same batch, before the test
  // can set up the desired state. Once the pending-commit lifecycle is wired to job
  // completion/failure events, the follow-up command will move there and CREATE can be used
  // directly in these tests.
  //
  // Pattern: pauseProcessing → reserve keys → stop → writeRecords(CREATED events) → start →
  // ENGINE.agentHistories().commit(). During replay, CREATED event appliers populate state;
  // COMMIT is then issued as a regular command and processed normally.

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

    ENGINE.pauseProcessing(1);
    final long itemKey =
        ((MutableProcessingState) ENGINE.getProcessingState()).getKeyGenerator().nextKey();
    ENGINE.stop();

    ENGINE.writeRecords(
        RecordToWrite.event()
            .key(itemKey)
            .agentHistory(
                AgentHistoryIntent.CREATED,
                new AgentHistoryRecord()
                    .setAgentHistoryKey(itemKey)
                    .setAgentInstanceKey(agentInstanceKey)
                    .setJobKey(jobKey)
                    .setElementInstanceKey(elementInstanceKey)
                    .setRole(AgentHistoryRole.USER)));
    ENGINE.start();

    ENGINE.agentHistories().withJobKey(jobKey).commit();

    // AgentHistoryCommitProcessor emits COMMITTED carrying the full stored record
    final var committed =
        RecordingExporter.agentHistoryRecords(AgentHistoryIntent.COMMITTED)
            .withRecordKey(itemKey)
            .getFirst();
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

    ENGINE.pauseProcessing(1);
    final var keyGenerator =
        ((MutableProcessingState) ENGINE.getProcessingState()).getKeyGenerator();
    final long firstItemKey = keyGenerator.nextKey();
    final long secondItemKey = keyGenerator.nextKey();
    final long otherJobItemKey = keyGenerator.nextKey();
    final long otherJobKey = keyGenerator.nextKey();
    ENGINE.stop();

    // Two items share jobKey but have different leases — COMMIT with no lease must commit both
    // regardless of lease. A third item with a different jobKey must not be committed.
    ENGINE.writeRecords(
        RecordToWrite.event()
            .key(firstItemKey)
            .agentHistory(
                AgentHistoryIntent.CREATED,
                new AgentHistoryRecord()
                    .setAgentHistoryKey(firstItemKey)
                    .setAgentInstanceKey(agentInstanceKey)
                    .setJobKey(jobKey)
                    .setJobLease("lease-a")
                    .setElementInstanceKey(elementInstanceKey)
                    .setRole(AgentHistoryRole.USER)),
        RecordToWrite.event()
            .key(secondItemKey)
            .agentHistory(
                AgentHistoryIntent.CREATED,
                new AgentHistoryRecord()
                    .setAgentHistoryKey(secondItemKey)
                    .setAgentInstanceKey(agentInstanceKey)
                    .setJobKey(jobKey)
                    .setJobLease("lease-b")
                    .setElementInstanceKey(elementInstanceKey)
                    .setRole(AgentHistoryRole.ASSISTANT)),
        RecordToWrite.event()
            .key(otherJobItemKey)
            .agentHistory(
                AgentHistoryIntent.CREATED,
                new AgentHistoryRecord()
                    .setAgentHistoryKey(otherJobItemKey)
                    .setAgentInstanceKey(agentInstanceKey)
                    .setJobKey(otherJobKey)
                    .setElementInstanceKey(elementInstanceKey)
                    .setRole(AgentHistoryRole.USER)));
    ENGINE.start();

    final var firstCommitted = ENGINE.agentHistories().withJobKey(jobKey).commit();
    final long commitPosition = firstCommitted.getSourceRecordPosition();
    final long clockResetKey = ENGINE.clock().reset().getKey();

    // COMMIT with no lease → visitByJobKey commits all items for jobKey regardless of lease.
    // withSourceRecordPosition scopes to events from this COMMIT command only; otherJobItemKey
    // is not visited by visitByJobKey(jobKey) so no event for it can appear.
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

    ENGINE.pauseProcessing(1);
    final var keyGenerator =
        ((MutableProcessingState) ENGINE.getProcessingState()).getKeyGenerator();
    final long lease1ItemKey = keyGenerator.nextKey();
    final long lease2ItemKey = keyGenerator.nextKey();
    final long otherJobItemKey = keyGenerator.nextKey();
    final long otherJobKey = keyGenerator.nextKey();
    ENGINE.stop();

    // lease-1 and lease-2 items share jobKey; otherJobItem has lease-1 but a different jobKey —
    // it must not be affected by the COMMIT, proving the filter is scoped to jobKey.
    ENGINE.writeRecords(
        RecordToWrite.event()
            .key(lease1ItemKey)
            .agentHistory(
                AgentHistoryIntent.CREATED,
                new AgentHistoryRecord()
                    .setAgentHistoryKey(lease1ItemKey)
                    .setAgentInstanceKey(agentInstanceKey)
                    .setJobKey(jobKey)
                    .setJobLease("lease-1")
                    .setElementInstanceKey(elementInstanceKey)
                    .setRole(AgentHistoryRole.USER)),
        RecordToWrite.event()
            .key(lease2ItemKey)
            .agentHistory(
                AgentHistoryIntent.CREATED,
                new AgentHistoryRecord()
                    .setAgentHistoryKey(lease2ItemKey)
                    .setAgentInstanceKey(agentInstanceKey)
                    .setJobKey(jobKey)
                    .setJobLease("lease-2")
                    .setElementInstanceKey(elementInstanceKey)
                    .setRole(AgentHistoryRole.ASSISTANT)),
        RecordToWrite.event()
            .key(otherJobItemKey)
            .agentHistory(
                AgentHistoryIntent.CREATED,
                new AgentHistoryRecord()
                    .setAgentHistoryKey(otherJobItemKey)
                    .setAgentInstanceKey(agentInstanceKey)
                    .setJobKey(otherJobKey)
                    .setJobLease("lease-1")
                    .setElementInstanceKey(elementInstanceKey)
                    .setRole(AgentHistoryRole.USER)));
    ENGINE.start();

    final var firstCommitted =
        ENGINE.agentHistories().withJobKey(jobKey).withJobLease("lease-1").commit();
    final long commitPosition = firstCommitted.getSourceRecordPosition();
    final long clockResetKey = ENGINE.clock().reset().getKey();

    // withSourceRecordPosition scopes to events from this COMMIT command only, naturally
    // excluding otherJobItem (different jobKey, not visited by visitByJobKey)
    final var agentHistoryEventsForCommit =
        RecordingExporter.records()
            .limit(r -> r.getKey() == clockResetKey)
            .withValueType(ValueType.AGENT_HISTORY)
            .filter(r -> r.getSourceRecordPosition() == commitPosition)
            .asList();

    // visitByJobLease(lease-1) → lease-1 item COMMITTED
    assertThat(
            agentHistoryEventsForCommit.stream()
                .filter(r -> r.getIntent() == AgentHistoryIntent.COMMITTED)
                .map(Record::getKey)
                .toList())
        .containsExactly(lease1ItemKey);

    // discard pass (visitByJobKey) → lease-2 item DISCARDED as superseded activation
    assertThat(
            agentHistoryEventsForCommit.stream()
                .filter(r -> r.getIntent() == AgentHistoryIntent.DISCARDED)
                .map(Record::getKey)
                .toList())
        .containsExactly(lease2ItemKey);

    // otherJobItem must not have any event (different jobKey, COMMIT is scoped to jobKey)
    assertThat(agentHistoryEventsForCommit.stream().map(Record::getKey).toList())
        .doesNotContain(otherJobItemKey);
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
}
