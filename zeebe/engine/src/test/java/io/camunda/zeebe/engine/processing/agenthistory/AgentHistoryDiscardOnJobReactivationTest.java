/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agenthistory;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies that re-activating an agentic job with a new lease (the job already held a lease from a
 * previous activation) emits a blanket {@code AGENT_HISTORY:DISCARD} for the job, so the prior
 * activation's pending items are discarded before the new lease's own history starts accumulating.
 */
public class AgentHistoryDiscardOnJobReactivationTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldDiscardPendingItemsOnlyAfterJobIsReactivatedWithNewLease() {
    // given — an agentic service task; the job is activated with a lease for the first time, and a
    // CONFIGURATION item lands under that lease via the agent instance's own creation batch.
    final var jobType = "reactivation-discard-fold";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask(
                    "agent-task", t -> t.zeebeJobType(jobType).zeebeAiAgentTaskDefinition())
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();
    final long elementInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId("agent-task")
            .getFirst()
            .getKey();

    final var firstBatch = ENGINE.jobs().withType(jobType).withTimeout(10L).withLease().activate();
    final long jobKey = firstBatch.getValue().getJobKeys().get(0);
    final String firstLease = firstBatch.getValue().getJobs().get(0).getLeaseToken();

    final var configurationItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-1")
            .setRole(AgentHistoryRole.CONFIGURATION)
            .setLoopIteration(1)
            .setModel("gpt-4o-mini")
            .setChangedAttributes(List.of("model"));
    final var created =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withJobLease(firstLease)
            .withHistory(List.of(configurationItem))
            .create();
    final long pendingItemKey = created.getValue().getHistory().get(0).getAgentHistoryKey();

    // when — the lease times out; a time-out never reactivates a leased job by itself, so no
    // reactivation discard should fire yet.
    ENGINE.increaseTime(EngineConfiguration.DEFAULT_JOBS_TIMEOUT_POLLING_INTERVAL);
    RecordingExporter.jobRecords(JobIntent.TIMED_OUT).withRecordKey(jobKey).getFirst();
    final long afterTimeoutKey = ENGINE.clock().reset().getKey();

    // then — the pending item is still untouched
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getKey() == afterTimeoutKey)
                .withValueType(ValueType.AGENT_HISTORY)
                .filter(r -> r.getIntent() == AgentHistoryIntent.DISCARD)
                .filter(r -> ((AgentHistoryRecordValue) r.getValue()).getJobKey() == jobKey)
                .exists())
        .isFalse();

    // when — a leasing worker re-activates the timed-out job, minting a new lease token
    final var secondBatch = ENGINE.jobs().withType(jobType).withLease().activate();
    assertThat(secondBatch.getValue().getJobKeys()).containsExactly(jobKey);
    final String secondLease = secondBatch.getValue().getJobs().get(0).getLeaseToken();
    assertThat(secondLease).isNotEmpty().isNotEqualTo(firstLease);

    // then — a blanket discard (no lease filter) is emitted for the job, and the first lease's
    // pending item is discarded
    final var discardCommand =
        RecordingExporter.agentHistoryRecords(AgentHistoryIntent.DISCARD)
            .onlyCommands()
            .withJobKey(jobKey)
            .getFirst();
    assertThat(discardCommand.getRecordType()).isEqualTo(RecordType.COMMAND);
    assertThat(discardCommand.getValue().getJobLease()).isEmpty();

    final var discarded =
        RecordingExporter.agentHistoryRecords(AgentHistoryIntent.DISCARDED)
            .withRecordKey(pendingItemKey)
            .getFirst();
    assertThat(discarded.getValue().getJobKey()).isEqualTo(jobKey);
  }

  @Test
  public void shouldNotDiscardOnFirstActivationOfAgenticJob() {
    // given/when — a fresh agentic job is activated with a lease for the very first time
    final var jobType = "reactivation-discard-first-activation";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask(
                    "agent-task", t -> t.zeebeJobType(jobType).zeebeAiAgentTaskDefinition())
                .endEvent()
                .done())
        .deploy();
    ENGINE.processInstance().ofBpmnProcessId("process").create();

    final var batch = ENGINE.jobs().withType(jobType).withLease().activate();
    final long jobKey = batch.getValue().getJobKeys().get(0);
    final long clockResetKey = ENGINE.clock().reset().getKey();

    // then — no re-activation discard is emitted; there was no prior lease to supersede
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getKey() == clockResetKey)
                .withValueType(ValueType.AGENT_HISTORY)
                .filter(r -> r.getIntent() == AgentHistoryIntent.DISCARD)
                .filter(r -> ((AgentHistoryRecordValue) r.getValue()).getJobKey() == jobKey)
                .exists())
        .isFalse();
  }

  @Test
  public void shouldNotDiscardWhenNonAgenticJobIsReactivatedWithNewLease() {
    // given — a plain (non-agentic) service task; the job is activated with a lease, then times out
    final var jobType = "reactivation-discard-non-agentic";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("plain-task", t -> t.zeebeJobType(jobType))
                .endEvent()
                .done())
        .deploy();
    ENGINE.processInstance().ofBpmnProcessId("process").create();

    final var firstBatch = ENGINE.jobs().withType(jobType).withTimeout(10L).withLease().activate();
    final long jobKey = firstBatch.getValue().getJobKeys().get(0);
    ENGINE.increaseTime(EngineConfiguration.DEFAULT_JOBS_TIMEOUT_POLLING_INTERVAL);
    RecordingExporter.jobRecords(JobIntent.TIMED_OUT).withRecordKey(jobKey).getFirst();

    // when — it is re-activated with a new lease
    final var secondBatch = ENGINE.jobs().withType(jobType).withLease().activate();
    assertThat(secondBatch.getValue().getJobKeys()).containsExactly(jobKey);
    final long clockResetKey = ENGINE.clock().reset().getKey();

    // then — no discard is emitted; the job is not agentic
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getKey() == clockResetKey)
                .withValueType(ValueType.AGENT_HISTORY)
                .filter(r -> r.getIntent() == AgentHistoryIntent.DISCARD)
                .filter(r -> ((AgentHistoryRecordValue) r.getValue()).getJobKey() == jobKey)
                .exists())
        .isFalse();
  }
}
