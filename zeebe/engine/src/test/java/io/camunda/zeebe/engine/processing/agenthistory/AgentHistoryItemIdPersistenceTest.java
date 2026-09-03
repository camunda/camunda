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
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryMessageContent;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryContentType;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Pins down that a history item's {@code historyItemId} survives the batch/update path: an item
 * pushed through {@code AGENT_INSTANCE:UPDATE}'s embedded {@code history[]} and applied by {@code
 * AgentHistoryBatchBehavior} is read back from state when its lease is committed or superseded, and
 * the {@code COMMITTED}/{@code DISCARDED} events re-emitted at that point must still carry the
 * item's original {@code historyItemId}.
 */
public class AgentHistoryItemIdPersistenceTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String SERVICE_TASK_ID = "agent-task";
  private static final String JOB_TYPE = "agentic-task";

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCarryHistoryItemIdOnCommittedAndDiscardedEvents() {
    // given — two activations of the same job, each pushing one history item under its own
    // lease via the batch/update path. Committing one lease commits its item and discards the
    // other lease's now-superseded pending item.
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
    final var elementInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SERVICE_TASK_ID)
            .getFirst()
            .getKey();
    final var agentInstanceKey =
        ENGINE.agentInstances().withElementInstanceKey(elementInstanceKey).create().getKey();
    ENGINE.jobs().withType(JOB_TYPE).activate();
    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(JOB_TYPE)
            .getFirst()
            .getKey();

    final var committedUpdate =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withJobLease("lease-committed")
            .withHistory(List.of(historyItem("history-item-committed")))
            .update();
    final long committedItemKey =
        committedUpdate.getValue().getHistory().get(0).getAgentHistoryKey();

    final var discardedUpdate =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withJobLease("lease-discarded")
            .withHistory(List.of(historyItem("history-item-discarded")))
            .update();
    final long discardedItemKey =
        discardedUpdate.getValue().getHistory().get(0).getAgentHistoryKey();

    // when
    ENGINE.agentHistories().withJobKey(jobKey).withJobLease("lease-committed").commit();

    // then
    assertThat(
            RecordingExporter.agentHistoryRecords(AgentHistoryIntent.COMMITTED)
                .withRecordKey(committedItemKey)
                .getFirst()
                .getValue()
                .getHistoryItemId())
        .describedAs("the COMMITTED event re-emitted from state must carry the original id")
        .isEqualTo("history-item-committed");
    assertThat(
            RecordingExporter.agentHistoryRecords(AgentHistoryIntent.DISCARDED)
                .withRecordKey(discardedItemKey)
                .getFirst()
                .getValue()
                .getHistoryItemId())
        .describedAs("the DISCARDED event re-emitted from state must carry the original id")
        .isEqualTo("history-item-discarded");
  }

  private static AgentHistoryRecord historyItem(final String historyItemId) {
    return new AgentHistoryRecord()
        .setHistoryItemId(historyItemId)
        .setRole(AgentHistoryRole.USER)
        .setLoopIteration(1)
        .addContent(
            new AgentHistoryMessageContent()
                .setContentType(AgentHistoryContentType.TEXT)
                .setText("hi"));
  }
}
