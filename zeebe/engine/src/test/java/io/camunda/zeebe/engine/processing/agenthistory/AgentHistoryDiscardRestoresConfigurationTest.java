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
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class AgentHistoryDiscardRestoresConfigurationTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRestoreLiveConfigurationToLastCommittedSnapshotOnDiscard() {
    // given — an agent instance whose CONFIGURATION item is applied and committed, so the
    // committed snapshot mirrors that change.
    final var jobType = "discard-restore-committed-snapshot";
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
    final long agentInstanceKey =
        ENGINE.agentInstances().withElementInstanceKey(elementInstanceKey).create().getKey();
    final long jobKey = ENGINE.jobs().withType(jobType).activate().getValue().getJobKeys().get(0);

    ENGINE
        .agentInstances()
        .withAgentInstanceKey(agentInstanceKey)
        .withElementInstanceKey(elementInstanceKey)
        .withJobKey(jobKey)
        .withHistory(
            List.of(
                new AgentHistoryRecord()
                    .setHistoryItemId("item-committed")
                    .setRole(AgentHistoryRole.CONFIGURATION)
                    .setLoopIteration(1)
                    .setModel("gpt-4o-mini")
                    .setChangedAttributes(List.of("model"))))
        .update();
    ENGINE.agentHistories().withJobKey(jobKey).commit();

    // when — a second CONFIGURATION change is applied but never committed, then discarded
    ENGINE
        .agentInstances()
        .withAgentInstanceKey(agentInstanceKey)
        .withElementInstanceKey(elementInstanceKey)
        .withJobKey(jobKey)
        .withHistory(
            List.of(
                new AgentHistoryRecord()
                    .setHistoryItemId("item-pending")
                    .setRole(AgentHistoryRole.CONFIGURATION)
                    .setLoopIteration(1)
                    .setModel("gpt-5")
                    .setChangedAttributes(List.of("model"))))
        .update();
    final var discardCommand = ENGINE.agentHistories().withJobKey(jobKey).discard();

    // then — the live definition is rolled back to the last committed value, not the pending one
    final var restored =
        RecordingExporter.agentInstanceRecords(AgentInstanceIntent.UPDATED)
            .filter(r -> r.getSourceRecordPosition() == discardCommand.getSourceRecordPosition())
            .getFirst();
    assertThat(restored.getValue().getDefinition().getModel()).isEqualTo("gpt-4o-mini");
    assertThat(restored.getValue().getChangedAttributes())
        .containsExactlyInAnyOrder(
            "model",
            "provider",
            "systemPrompt",
            "tools",
            "maxTokens",
            "maxModelCalls",
            "maxToolCalls");
  }

  @Test
  public void shouldRestoreToEmptyDefaultsWhenDiscardingBeforeAnyCommitEverHappened() {
    // given — a CONFIGURATION item is applied but discarded before any item for this agent
    // instance was ever committed, so no snapshot exists yet.
    final var jobType = "discard-restore-no-snapshot";
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
    final long agentInstanceKey =
        ENGINE.agentInstances().withElementInstanceKey(elementInstanceKey).create().getKey();
    final long jobKey = ENGINE.jobs().withType(jobType).activate().getValue().getJobKeys().get(0);

    // when
    ENGINE
        .agentInstances()
        .withAgentInstanceKey(agentInstanceKey)
        .withElementInstanceKey(elementInstanceKey)
        .withJobKey(jobKey)
        .withHistory(
            List.of(
                new AgentHistoryRecord()
                    .setHistoryItemId("item-pending")
                    .setRole(AgentHistoryRole.CONFIGURATION)
                    .setLoopIteration(1)
                    .setModel("gpt-4o-mini")
                    .setChangedAttributes(List.of("model"))))
        .update();
    final var discardCommand = ENGINE.agentHistories().withJobKey(jobKey).discard();

    // then — restored to the AgentInstanceRecord defaults (empty), not the pre-change value
    final var restored =
        RecordingExporter.agentInstanceRecords(AgentInstanceIntent.UPDATED)
            .filter(r -> r.getSourceRecordPosition() == discardCommand.getSourceRecordPosition())
            .getFirst();
    assertThat(restored.getValue().getDefinition().getModel()).isEmpty();
    assertThat(restored.getValue().getChangedAttributes())
        .containsExactlyInAnyOrder(
            "model",
            "provider",
            "systemPrompt",
            "tools",
            "maxTokens",
            "maxModelCalls",
            "maxToolCalls");
  }
}
