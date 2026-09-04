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
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class AgentInstanceCleanedApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableAgentHistoryState agentHistoryState;
  private AgentInstanceCleanedApplier cleanedApplier;

  @BeforeEach
  public void setup() {
    agentHistoryState = processingState.getAgentHistoryState();
    cleanedApplier = new AgentInstanceCleanedApplier(agentHistoryState);
  }

  @Test
  void shouldDeleteCommittedHistoryItemIdsGivenInEvent() {
    // given — committed ids recorded against an agent instance.
    final long agentInstanceKey = 21L;
    agentHistoryState.putCommittedHistoryItemKey(agentInstanceKey, "history-item-1", 101L);
    agentHistoryState.putCommittedHistoryItemKey(agentInstanceKey, "history-item-2", 102L);

    // when — the CLEANED event lists both ids for deletion.
    cleanedApplier.applyState(
        agentInstanceKey,
        new AgentInstanceRecord()
            .setHistoryItemIdsToDelete(List.of("history-item-1", "history-item-2")));

    // then — both committed ids are gone.
    assertThat(agentHistoryState.getCommittedHistoryItemKey(agentInstanceKey, "history-item-1"))
        .isNull();
    assertThat(agentHistoryState.getCommittedHistoryItemKey(agentInstanceKey, "history-item-2"))
        .isNull();
  }

  @Test
  void shouldDeleteMetricsAccumulatedIdsGivenInEvent() {
    // given — metrics-accumulated ids recorded against an agent instance.
    final long agentInstanceKey = 31L;
    agentHistoryState.markMetricsAccumulated(agentInstanceKey, "history-item-1");
    agentHistoryState.markMetricsAccumulated(agentInstanceKey, "history-item-2");

    // when — the CLEANED event lists both ids for deletion.
    cleanedApplier.applyState(
        agentInstanceKey,
        new AgentInstanceRecord()
            .setHistoryItemIdsToDelete(List.of("history-item-1", "history-item-2")));

    // then — both metrics-accumulated ids are gone.
    assertThat(agentHistoryState.hasAccumulatedMetrics(agentInstanceKey, "history-item-1"))
        .isFalse();
    assertThat(agentHistoryState.hasAccumulatedMetrics(agentInstanceKey, "history-item-2"))
        .isFalse();
  }

  @Test
  void shouldDeleteFromBothIndexesRegardlessOfOrigin() {
    // given — one id only in the committed index, another only in the metrics-accumulated index.
    final long agentInstanceKey = 41L;
    agentHistoryState.putCommittedHistoryItemKey(agentInstanceKey, "history-item-1", 101L);
    agentHistoryState.markMetricsAccumulated(agentInstanceKey, "history-item-2");

    // when — the CLEANED event lists both ids for deletion.
    cleanedApplier.applyState(
        agentInstanceKey,
        new AgentInstanceRecord()
            .setHistoryItemIdsToDelete(List.of("history-item-1", "history-item-2")));

    // then — each id is gone from whichever index it was actually in, and deleting from the
    // index it was never in was a harmless no-op.
    assertThat(agentHistoryState.getCommittedHistoryItemKey(agentInstanceKey, "history-item-1"))
        .isNull();
    assertThat(agentHistoryState.hasAccumulatedMetrics(agentInstanceKey, "history-item-2"))
        .isFalse();
  }

  @Test
  void shouldNotAffectOtherAgentInstances() {
    // given — committed ids for two different agent instances.
    final long firstAgentInstanceKey = 22L;
    final long secondAgentInstanceKey = 23L;
    agentHistoryState.putCommittedHistoryItemKey(firstAgentInstanceKey, "history-item-1", 101L);
    agentHistoryState.putCommittedHistoryItemKey(secondAgentInstanceKey, "history-item-1", 201L);

    // when — only the first agent instance is cleaned up.
    cleanedApplier.applyState(
        firstAgentInstanceKey,
        new AgentInstanceRecord().setHistoryItemIdsToDelete(List.of("history-item-1")));

    // then — the second agent instance's committed id is untouched.
    assertThat(
            agentHistoryState.getCommittedHistoryItemKey(secondAgentInstanceKey, "history-item-1"))
        .isEqualTo(201L);
  }
}
