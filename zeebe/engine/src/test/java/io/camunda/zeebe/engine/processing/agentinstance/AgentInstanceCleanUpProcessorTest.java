/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agentinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryMessageContent;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryContentType;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Covers {@code AGENT_INSTANCE:CLEAN_UP} — the bounded, self-chaining cleanup of committed/
 * metrics-accumulated history-item ids kicked off right after an agent instance completes. Runs
 * with a small {@code agentHistoryCleanupChunkSize} so the chunking/self-chaining behavior can be
 * exercised without needing hundreds of history items per test.
 */
public class AgentInstanceCleanUpProcessorTest {

  private static final int CHUNK_SIZE = 2;

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withEngineConfig(c -> c.setAgentHistoryCleanupChunkSize(CHUNK_SIZE));

  private static final String PROCESS_ID = "process";
  private static final String SERVICE_TASK_ID = "agent-task";

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldCleanUpCommittedHistoryItemIdsOnCompletion() {
    // given — one committed history item, within a single chunk.
    final var fixture = deployAndCreateAgentInstance();
    commitHistoryItems(fixture, "item-1");

    // when
    ENGINE.agentInstances().withProcessInstanceKey(fixture.processInstanceKey()).complete();

    // then — a single CLEANED event lists exactly that id, and it's gone from committed state.
    final var cleaned =
        RecordingExporter.agentInstanceRecords(AgentInstanceIntent.CLEANED)
            .withRecordKey(fixture.agentInstanceKey())
            .getFirst();
    assertThat(cleaned.getValue().getHistoryItemIdsToDelete()).containsExactly("item-1");
    assertThat(
            ENGINE
                .getProcessingState()
                .getAgentHistoryState()
                .getCommittedHistoryItemKey(fixture.agentInstanceKey(), "item-1"))
        .isNull();
  }

  @Test
  public void shouldChunkCleanupAcrossMultipleCyclesWhenExceedingChunkSize() {
    // given — three committed history items, one more than the chunk size of two.
    final var fixture = deployAndCreateAgentInstance();
    commitHistoryItems(fixture, "item-1", "item-2", "item-3");

    // when
    ENGINE.agentInstances().withProcessInstanceKey(fixture.processInstanceKey()).complete();

    // then — two self-chained CLEANED cycles, chunked two-then-one, together covering every id
    // exactly once.
    final var cleanedEvents =
        RecordingExporter.agentInstanceRecords(AgentInstanceIntent.CLEANED)
            .withRecordKey(fixture.agentInstanceKey())
            .limit(2)
            .toList();
    assertThat(cleanedEvents)
        .extracting(e -> e.getValue().getHistoryItemIdsToDelete().size())
        .containsExactly(CHUNK_SIZE, 1);
    assertThat(
            cleanedEvents.stream().flatMap(e -> e.getValue().getHistoryItemIdsToDelete().stream()))
        .containsExactlyInAnyOrder("item-1", "item-2", "item-3");
    assertThat(
            IntStream.rangeClosed(1, 3)
                .mapToObj(
                    i ->
                        ENGINE
                            .getProcessingState()
                            .getAgentHistoryState()
                            .getCommittedHistoryItemKey(fixture.agentInstanceKey(), "item-" + i)))
        .allMatch(java.util.Objects::isNull);
  }

  @Test
  public void shouldNotEmitCleanedEventWhenNothingToCleanUp() {
    // given — no history items at all for this agent instance.
    final var fixture = deployAndCreateAgentInstance();

    // when
    ENGINE.agentInstances().withProcessInstanceKey(fixture.processInstanceKey()).complete();

    // then — no CLEANED event is ever written for it; bound the negative check with a clock
    // reset so it doesn't just pass because the exporter hasn't caught up yet.
    final var clockResetKey = ENGINE.clock().reset().getKey();
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getKey() == clockResetKey)
                .withValueType(ValueType.AGENT_INSTANCE)
                .withIntent(AgentInstanceIntent.CLEANED)
                .filter(r -> r.getKey() == fixture.agentInstanceKey())
                .exists())
        .isFalse();
  }

  private void commitHistoryItems(final AgentInstanceFixture fixture, final String... itemIds) {
    ENGINE.jobs().withType(helper.getJobType()).activate();
    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(fixture.processInstanceKey())
            .withType(helper.getJobType())
            .getFirst()
            .getKey();
    final var items =
        List.of(itemIds).stream().map(AgentInstanceCleanUpProcessorTest::historyItem).toList();
    ENGINE
        .agentInstances()
        .withAgentInstanceKey(fixture.agentInstanceKey())
        .withElementInstanceKey(fixture.elementInstanceKey())
        .withJobKey(jobKey)
        .withHistory(items)
        .update();
    ENGINE
        .agentHistories()
        .withAgentInstanceKey(fixture.agentInstanceKey())
        .withJobKey(jobKey)
        .commit();
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

  private AgentInstanceFixture deployAndCreateAgentInstance() {
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(
                    SERVICE_TASK_ID,
                    t -> t.zeebeJobType(helper.getJobType()).zeebeAiAgentTaskDefinition())
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SERVICE_TASK_ID)
            .getFirst();
    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .create()
            .getValue()
            .getAgentInstanceKey();
    return new AgentInstanceFixture(
        processInstanceKey, agentInstanceKey, serviceTaskInstance.getKey());
  }

  private record AgentInstanceFixture(
      long processInstanceKey, long agentInstanceKey, long elementInstanceKey) {}
}
