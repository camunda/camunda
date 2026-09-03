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
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
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
 * <p>COMMIT and DISCARD are unconditionally classified {@code PROCESS}, so teardown bookkeeping
 * cannot be orphaned when a suspended instance is cancelled.
 */
public class AgentHistorySuspensionGateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String JOB_TYPE = "agentic-task";

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

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
    final long historyItemKey =
        createHistoryItem(agentInstanceKey, jobKey, elementInstanceKey).getKey();

    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    ENGINE.writeRecords(
        RecordToWrite.command()
            .agentHistory(
                AgentHistoryIntent.DISCARD,
                new AgentHistoryRecord()
                    .setJobKey(jobKey)
                    .setProcessInstanceKey(processInstanceKey)));

    // then — DISCARD succeeds while the process instance is suspended (PROCESS classification)
    assertThat(
            RecordingExporter.agentHistoryRecords(AgentHistoryIntent.DISCARDED)
                .withRecordKey(historyItemKey)
                .getFirst())
        .isNotNull();
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
    final long historyItemKey =
        createHistoryItem(agentInstanceKey, jobKey, elementInstanceKey).getKey();

    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    ENGINE.writeRecords(
        RecordToWrite.command()
            .agentHistory(
                AgentHistoryIntent.COMMIT,
                new AgentHistoryRecord()
                    .setJobKey(jobKey)
                    .setProcessInstanceKey(processInstanceKey)));

    // then — COMMIT succeeds while the process instance is suspended (PROCESS classification)
    assertThat(
            RecordingExporter.agentHistoryRecords(AgentHistoryIntent.COMMITTED)
                .withRecordKey(historyItemKey)
                .getFirst())
        .isNotNull();
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

  private static Record<?> createHistoryItem(
      final long agentInstanceKey, final long jobKey, final long elementInstanceKey) {
    return ENGINE
        .agentHistories()
        .withAgentInstanceKey(agentInstanceKey)
        .withJobKey(jobKey)
        .withElementInstanceKey(elementInstanceKey)
        .withRole(AgentHistoryRole.USER)
        .create();
  }
}
