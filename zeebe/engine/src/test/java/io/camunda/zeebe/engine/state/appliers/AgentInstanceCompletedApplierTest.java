/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableAgentHistoryState;
import io.camunda.zeebe.engine.state.mutable.MutableAgentInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class AgentInstanceCompletedApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableAgentInstanceState agentInstanceState;
  private MutableAgentHistoryState agentHistoryState;
  private AgentInstanceCreatedApplier createdApplier;
  private AgentInstanceCompletedApplier completedApplier;

  @BeforeEach
  public void setup() {
    agentInstanceState = processingState.getAgentInstanceState();
    agentHistoryState = processingState.getAgentHistoryState();
    final MutableElementInstanceState elementInstanceState =
        processingState.getElementInstanceState();
    createdApplier = new AgentInstanceCreatedApplier(agentInstanceState, elementInstanceState);
    completedApplier = new AgentInstanceCompletedApplier(agentInstanceState, agentHistoryState);
  }

  @Test
  void shouldDeleteRecordAndRemoveFromProcessInstanceKeyIndex() {
    // given — a previously CREATED record associated with a process instance.
    final long agentInstanceKey = 9L;
    final long processInstanceKey = 5L;
    createdApplier.applyState(
        agentInstanceKey,
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setProcessInstanceKey(processInstanceKey)
            .setStatus(AgentInstanceStatus.INITIALIZING));

    // when — apply the COMPLETED event.
    completedApplier.applyState(
        agentInstanceKey,
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setProcessInstanceKey(processInstanceKey)
            .setStatus(AgentInstanceStatus.COMPLETED));

    // then — the record is gone from primary state, and the process-instance-key secondary
    // index no longer returns the deleted key.
    assertThat(agentInstanceState.getRecord(agentInstanceKey)).isNull();
    assertThat(agentInstanceState.getAgentInstanceKeysByProcessInstanceKey(processInstanceKey))
        .isEmpty();
  }

  @Test
  void shouldNotAffectOtherAgentInstancesOfSameProcessInstance() {
    // given — two agent instances associated with the same process instance.
    final long processInstanceKey = 5L;
    final long firstAgentInstanceKey = 11L;
    final long secondAgentInstanceKey = 12L;
    createdApplier.applyState(
        firstAgentInstanceKey,
        new AgentInstanceRecord()
            .setAgentInstanceKey(firstAgentInstanceKey)
            .setProcessInstanceKey(processInstanceKey)
            .setStatus(AgentInstanceStatus.INITIALIZING));
    createdApplier.applyState(
        secondAgentInstanceKey,
        new AgentInstanceRecord()
            .setAgentInstanceKey(secondAgentInstanceKey)
            .setProcessInstanceKey(processInstanceKey)
            .setStatus(AgentInstanceStatus.INITIALIZING));

    // when — only the first agent instance is completed.
    completedApplier.applyState(
        firstAgentInstanceKey,
        new AgentInstanceRecord()
            .setAgentInstanceKey(firstAgentInstanceKey)
            .setProcessInstanceKey(processInstanceKey)
            .setStatus(AgentInstanceStatus.COMPLETED));

    // then — the second agent instance is untouched, in both primary state and the index.
    assertThat(agentInstanceState.getRecord(secondAgentInstanceKey)).isNotNull();
    assertThat(agentInstanceState.getAgentInstanceKeysByProcessInstanceKey(processInstanceKey))
        .containsExactly(secondAgentInstanceKey);
  }

  @Test
  void shouldDeleteCommittedHistoryItemIdsOnCompletion() {
    // given — a completed agent instance with committed history item ids recorded against it.
    final long agentInstanceKey = 21L;
    final long processInstanceKey = 5L;
    createdApplier.applyState(
        agentInstanceKey,
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setProcessInstanceKey(processInstanceKey)
            .setStatus(AgentInstanceStatus.INITIALIZING));
    agentHistoryState.putCommittedHistoryItemKey(agentInstanceKey, "history-item-1", 101L);
    agentHistoryState.putCommittedHistoryItemKey(agentInstanceKey, "history-item-2", 102L);

    // when — the agent instance completes.
    completedApplier.applyState(
        agentInstanceKey,
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setProcessInstanceKey(processInstanceKey)
            .setStatus(AgentInstanceStatus.COMPLETED));

    // then — every committed id recorded for this agent instance is gone.
    assertThat(agentHistoryState.getCommittedHistoryItemKey(agentInstanceKey, "history-item-1"))
        .isNull();
    assertThat(agentHistoryState.getCommittedHistoryItemKey(agentInstanceKey, "history-item-2"))
        .isNull();
  }

  @Test
  void shouldDeleteCommittedHistoryItemIdsAtScaleOnCompletion() {
    // given — an agent instance that accumulated more than 1,000 committed history-item ids,
    // seeded directly against state rather than by applying 1,500 real COMMITTED events, since
    // only the collect-then-delete cleanup in the applier is under test here
    final long agentInstanceKey = 24L;
    final long processInstanceKey = 5L;
    final int committedIdCount = 1500;
    createdApplier.applyState(
        agentInstanceKey,
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setProcessInstanceKey(processInstanceKey)
            .setStatus(AgentInstanceStatus.INITIALIZING));
    IntStream.range(0, committedIdCount)
        .forEach(
            i ->
                agentHistoryState.putCommittedHistoryItemKey(
                    agentInstanceKey, "history-item-" + i, i));

    // when — the agent instance completes.
    completedApplier.applyState(
        agentInstanceKey,
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setProcessInstanceKey(processInstanceKey)
            .setStatus(AgentInstanceStatus.COMPLETED));

    // then — every committed id seeded for this agent instance is gone.
    IntStream.range(0, committedIdCount)
        .forEach(
            i ->
                assertThat(
                        agentHistoryState.getCommittedHistoryItemKey(
                            agentInstanceKey, "history-item-" + i))
                    .describedAs("committed id history-item-%d should be removed on completion", i)
                    .isNull());
  }

  @Test
  void shouldNotAffectCommittedHistoryItemIdsOfOtherAgentInstances() {
    // given — committed ids for two different agent instances.
    final long processInstanceKey = 5L;
    final long firstAgentInstanceKey = 22L;
    final long secondAgentInstanceKey = 23L;
    createdApplier.applyState(
        firstAgentInstanceKey,
        new AgentInstanceRecord()
            .setAgentInstanceKey(firstAgentInstanceKey)
            .setProcessInstanceKey(processInstanceKey)
            .setStatus(AgentInstanceStatus.INITIALIZING));
    agentHistoryState.putCommittedHistoryItemKey(firstAgentInstanceKey, "history-item-1", 101L);
    agentHistoryState.putCommittedHistoryItemKey(secondAgentInstanceKey, "history-item-1", 201L);

    // when — only the first agent instance completes.
    completedApplier.applyState(
        firstAgentInstanceKey,
        new AgentInstanceRecord()
            .setAgentInstanceKey(firstAgentInstanceKey)
            .setProcessInstanceKey(processInstanceKey)
            .setStatus(AgentInstanceStatus.COMPLETED));

    // then — the second agent instance's committed id is untouched.
    assertThat(
            agentHistoryState.getCommittedHistoryItemKey(secondAgentInstanceKey, "history-item-1"))
        .isEqualTo(201L);
  }

  @Test
  void shouldDeleteMetricsAccumulatedIdsOnCompletion() {
    // given — a completed agent instance with metrics-accumulated ids recorded against it.
    final long agentInstanceKey = 31L;
    final long processInstanceKey = 5L;
    createdApplier.applyState(
        agentInstanceKey,
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setProcessInstanceKey(processInstanceKey)
            .setStatus(AgentInstanceStatus.INITIALIZING));
    agentHistoryState.markMetricsAccumulated(agentInstanceKey, "history-item-1");
    agentHistoryState.markMetricsAccumulated(agentInstanceKey, "history-item-2");

    // when — the agent instance completes.
    completedApplier.applyState(
        agentInstanceKey,
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setProcessInstanceKey(processInstanceKey)
            .setStatus(AgentInstanceStatus.COMPLETED));

    // then — every metrics-accumulated id recorded for this agent instance is gone.
    assertThat(agentHistoryState.hasAccumulatedMetrics(agentInstanceKey, "history-item-1"))
        .isFalse();
    assertThat(agentHistoryState.hasAccumulatedMetrics(agentInstanceKey, "history-item-2"))
        .isFalse();
  }

  @Test
  void shouldNotAffectMetricsAccumulatedIdsOfOtherAgentInstances() {
    // given — metrics-accumulated ids for two different agent instances.
    final long processInstanceKey = 5L;
    final long firstAgentInstanceKey = 32L;
    final long secondAgentInstanceKey = 33L;
    createdApplier.applyState(
        firstAgentInstanceKey,
        new AgentInstanceRecord()
            .setAgentInstanceKey(firstAgentInstanceKey)
            .setProcessInstanceKey(processInstanceKey)
            .setStatus(AgentInstanceStatus.INITIALIZING));
    agentHistoryState.markMetricsAccumulated(firstAgentInstanceKey, "history-item-1");
    agentHistoryState.markMetricsAccumulated(secondAgentInstanceKey, "history-item-1");

    // when — only the first agent instance completes.
    completedApplier.applyState(
        firstAgentInstanceKey,
        new AgentInstanceRecord()
            .setAgentInstanceKey(firstAgentInstanceKey)
            .setProcessInstanceKey(processInstanceKey)
            .setStatus(AgentInstanceStatus.COMPLETED));

    // then — the second agent instance's metrics-accumulated id is untouched.
    assertThat(agentHistoryState.hasAccumulatedMetrics(secondAgentInstanceKey, "history-item-1"))
        .isTrue();
  }
}
