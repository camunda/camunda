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
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceTool;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
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

    final var committedItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-committed")
            .setRole(AgentHistoryRole.CONFIGURATION)
            .setLoopIteration(1)
            .setModel("gpt-4o-mini")
            .setProvider("openai");
    committedItem.addSystemPrompt(
        new AgentHistoryMessageContent()
            .setContentType(AgentHistoryContentType.TEXT)
            .setText("Committed prompt."));
    committedItem.setTools(
        List.of(new AgentInstanceTool().setName("search").setElementId("search-task")));
    committedItem.getLimits().setMaxTokens(4096L).setMaxModelCalls(10).setMaxToolCalls(20);
    committedItem.setChangedAttributes(
        List.of(
            "model",
            "provider",
            "systemPrompt",
            "tools",
            "maxTokens",
            "maxModelCalls",
            "maxToolCalls"));
    ENGINE
        .agentInstances()
        .withAgentInstanceKey(agentInstanceKey)
        .withElementInstanceKey(elementInstanceKey)
        .withJobKey(jobKey)
        .withHistory(List.of(committedItem))
        .update();
    ENGINE.agentHistories().withJobKey(jobKey).commit();

    // when — a second CONFIGURATION change, with distinct values for every field, is applied but
    // never committed, then discarded
    final var pendingItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-pending")
            .setRole(AgentHistoryRole.CONFIGURATION)
            .setLoopIteration(1)
            .setModel("gpt-5")
            .setProvider("anthropic");
    pendingItem.addSystemPrompt(
        new AgentHistoryMessageContent()
            .setContentType(AgentHistoryContentType.TEXT)
            .setText("Pending prompt."));
    pendingItem.setTools(
        List.of(new AgentInstanceTool().setName("calc").setElementId("calc-task")));
    pendingItem.getLimits().setMaxTokens(8192L).setMaxModelCalls(99).setMaxToolCalls(77);
    pendingItem.setChangedAttributes(
        List.of(
            "model",
            "provider",
            "systemPrompt",
            "tools",
            "maxTokens",
            "maxModelCalls",
            "maxToolCalls"));
    ENGINE
        .agentInstances()
        .withAgentInstanceKey(agentInstanceKey)
        .withElementInstanceKey(elementInstanceKey)
        .withJobKey(jobKey)
        .withHistory(List.of(pendingItem))
        .update();
    final var discardCommand = ENGINE.agentHistories().withJobKey(jobKey).discard();

    // then — every field is rolled back to its last committed value, not the pending one
    final var restored =
        RecordingExporter.agentInstanceRecords(AgentInstanceIntent.UPDATED)
            .filter(r -> r.getSourceRecordPosition() == discardCommand.getSourceRecordPosition())
            .getFirst();
    assertThat(restored.getValue().getDefinition().getModel()).isEqualTo("gpt-4o-mini");
    assertThat(restored.getValue().getDefinition().getProvider()).isEqualTo("openai");
    assertThat(restored.getValue().getDefinition().getSystemPrompt())
        .hasSize(1)
        .first()
        .satisfies(block -> assertThat(block.getText()).isEqualTo("Committed prompt."));
    assertThat(restored.getValue().getTools()).extracting("name").containsExactly("search");
    assertThat(restored.getValue().getLimits().getMaxTokens()).isEqualTo(4096L);
    assertThat(restored.getValue().getLimits().getMaxModelCalls()).isEqualTo(10);
    assertThat(restored.getValue().getLimits().getMaxToolCalls()).isEqualTo(20);
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
