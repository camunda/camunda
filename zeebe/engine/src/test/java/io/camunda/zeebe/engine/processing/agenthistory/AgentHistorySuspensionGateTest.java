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
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceBufferedCommandRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies the suspension gate classifications of AgentHistory processors.
 *
 * <p>CREATE is classified {@code isInternalCommand() ? BUFFER : REJECT}:
 *
 * <ul>
 *   <li>Internal commands are BUFFER'd so they can be replayed when the instance resumes.
 *   <li>External commands would be REJECT'd to prevent an authorization bypass: a buffered command
 *       is drained as an internal command, skipping the CSL check.
 * </ul>
 *
 * <p>COMMIT and DISCARD are classified {@code PROCESS} so that teardown bookkeeping cannot be
 * orphaned when a suspended instance is cancelled.
 *
 * <p>For CREATE, {@code processInstanceKey} must be present on the command record for the gate to
 * fire. Production clients only carry other keys, so the gate returns PROCESS early for real
 * external commands; the test below supplies it explicitly via {@link RecordToWrite}. {@link
 * RecordToWrite#command()} produces internal commands (no request metadata), so the BUFFER path is
 * exercised rather than REJECT.
 */
public class AgentHistorySuspensionGateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String JOB_TYPE = JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX;

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldBufferInternalCreateCommandWhileSuspended() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final String taskId = Strings.newRandomValidBpmnId();
    final var serviceTaskActivated = deployAndCreateProcessInstance(processId, taskId);
    final long elementInstanceKey = serviceTaskActivated.getKey();
    final long processInstanceKey = serviceTaskActivated.getValue().getProcessInstanceKey();

    final long agentInstanceKey = createAgentInstance(elementInstanceKey).getKey();
    final long jobKey = activateJobForProcessInstance(processInstanceKey);

    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when — supply processInstanceKey explicitly so the suspension gate can resolve the target PI.
    // RecordToWrite.command() produces an internal command, so the gate classifies it as BUFFER.
    final var commandRecord =
        new AgentHistoryRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setJobKey(jobKey)
            .setElementInstanceKey(elementInstanceKey)
            .setProcessInstanceKey(processInstanceKey)
            .setRole(AgentHistoryRole.USER);
    ENGINE.writeRecords(
        RecordToWrite.command().agentHistory(AgentHistoryIntent.CREATE, commandRecord));

    // then — the gate queues the internal command; it will be drained when the instance resumes
    final Record<RecordValue> buffered =
        RecordingExporter.records()
            .withValueType(ValueType.PROCESS_INSTANCE_BUFFERED_COMMAND)
            .withIntent(ProcessInstanceBufferedCommandIntent.BUFFERED)
            .filter(
                r -> {
                  final var v = (ProcessInstanceBufferedCommandRecordValue) r.getValue();
                  return v.getProcessInstanceKey() == processInstanceKey
                      && v.getValueType() == ValueType.AGENT_HISTORY;
                })
            .getFirst();
    assertThat(((ProcessInstanceBufferedCommandRecordValue) buffered.getValue()).getIntent())
        .isEqualTo(AgentHistoryIntent.CREATE);
  }

  @Test
  public void shouldProcessDiscardCommandWhileSuspended() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final String taskId = Strings.newRandomValidBpmnId();
    final var serviceTaskActivated = deployAndCreateProcessInstance(processId, taskId);
    final long elementInstanceKey = serviceTaskActivated.getKey();
    final long processInstanceKey = serviceTaskActivated.getValue().getProcessInstanceKey();

    final long agentInstanceKey = createAgentInstance(elementInstanceKey).getKey();
    final long jobKey = activateJobForProcessInstance(processInstanceKey);
    createHistoryItem(agentInstanceKey, jobKey, elementInstanceKey);

    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final var result = ENGINE.agentHistories().withJobKey(jobKey).discard();

    // then — DISCARD succeeds while the process instance is suspended (PROCESS classification)
    assertThat(result.getIntent()).isEqualTo(AgentHistoryIntent.DISCARDED);
  }

  @Test
  public void shouldProcessCommitCommandWhileSuspended() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final String taskId = Strings.newRandomValidBpmnId();
    final var serviceTaskActivated = deployAndCreateProcessInstance(processId, taskId);
    final long elementInstanceKey = serviceTaskActivated.getKey();
    final long processInstanceKey = serviceTaskActivated.getValue().getProcessInstanceKey();

    final long agentInstanceKey = createAgentInstance(elementInstanceKey).getKey();
    final long jobKey = activateJobForProcessInstance(processInstanceKey);
    createHistoryItem(agentInstanceKey, jobKey, elementInstanceKey);

    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final var result = ENGINE.agentHistories().withJobKey(jobKey).commit();

    // then — COMMIT succeeds while the process instance is suspended (PROCESS classification)
    assertThat(result.getIntent()).isEqualTo(AgentHistoryIntent.COMMITTED);
  }

  // --- helpers ---

  private static Record<ProcessInstanceRecordValue> deployAndCreateProcessInstance(
      final String processId, final String taskId) {
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(taskId, t -> t.zeebeJobType(JOB_TYPE).zeebeAiAgentTaskDefinition())
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .withElementId(taskId)
        .getFirst();
  }

  private static Record<?> createAgentInstance(final long elementInstanceKey) {
    return ENGINE
        .agentInstances()
        .withElementInstanceKey(elementInstanceKey)
        .withDefinition("gpt-4o", "openai", "sys")
        .create();
  }

  private static long activateJobForProcessInstance(final long processInstanceKey) {
    ENGINE.jobs().withType(JOB_TYPE).activate();
    return RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(JOB_TYPE)
        .getFirst()
        .getKey();
  }

  private static void createHistoryItem(
      final long agentInstanceKey, final long jobKey, final long elementInstanceKey) {
    ENGINE
        .agentHistories()
        .withAgentInstanceKey(agentInstanceKey)
        .withJobKey(jobKey)
        .withElementInstanceKey(elementInstanceKey)
        .withRole(AgentHistoryRole.USER)
        .create();
  }
}
