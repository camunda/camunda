/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.agenthistory;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.immutable.AgentHistoryState.AgentHistoryVisitor;
import io.camunda.zeebe.engine.state.mutable.MutableAgentHistoryState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class DbAgentHistoryStateTest {

  private MutableProcessingState processingState;
  private MutableAgentHistoryState state;

  @BeforeEach
  void beforeEach() {
    state = processingState.getAgentHistoryState();
  }

  @Test
  void shouldInsertAndGetByKey() {
    // given
    final long historyItemKey = 1L;
    final var record = sampleRecord(100L, "lease-a");

    // when
    state.insert(historyItemKey, record);

    // then
    final var stored = state.get(historyItemKey);
    assertThat(stored).isNotNull();
    assertThat(stored.getJobKey()).isEqualTo(100L);
    assertThat(stored.getJobLease()).isEqualTo("lease-a");
  }

  @Test
  void shouldReturnNullForUnknownKey() {
    // given / when / then
    assertThat(state.get(999L)).isNull();
  }

  @Test
  void shouldVisitAllItemsByJobKey() {
    // given
    final long jobKey = 42L;
    state.insert(1L, sampleRecord(jobKey, "lease-a"));
    state.insert(2L, sampleRecord(jobKey, "lease-b"));
    state.insert(3L, sampleRecord(99L, "lease-x")); // different job — should not appear

    // when
    final List<Long> visited = collectJobKeys(v -> state.visitByJobKey(jobKey, v));

    // then
    assertThat(visited).containsExactlyInAnyOrder(jobKey, jobKey);
  }

  @Test
  void shouldVisitItemsByJobLease() {
    // given
    final long jobKey = 42L;
    state.insert(1L, sampleRecord(jobKey, "lease-a"));
    state.insert(2L, sampleRecord(jobKey, "lease-a"));
    state.insert(3L, sampleRecord(jobKey, "lease-b")); // different lease — should not appear

    // when
    final List<String> visited = new ArrayList<>();
    state.visitByJobLease(jobKey, "lease-a", item -> visited.add(item.getJobLease()));

    // then
    assertThat(visited).hasSize(2).allMatch("lease-a"::equals);
  }

  @Test
  void shouldDeleteFromBothColumnFamilies() {
    // given
    final long historyItemKey = 1L;
    final long jobKey = 42L;
    state.insert(historyItemKey, sampleRecord(jobKey, "lease-a"));

    // when
    state.delete(historyItemKey);

    // then: primary lookup returns null
    assertThat(state.get(historyItemKey)).isNull();

    // then: secondary index no longer contains the item
    final List<Long> visited = collectJobKeys(v -> state.visitByJobKey(jobKey, v));
    assertThat(visited).isEmpty();
  }

  @Test
  void shouldIgnoreDeleteOfUnknownKey() {
    // given / when / then — must not throw
    state.delete(999L);
  }

  @Test
  void shouldVisitByJobKeyOnlyMatchingJobKey() {
    // given
    state.insert(1L, sampleRecord(10L, "lease-a"));
    state.insert(2L, sampleRecord(20L, "lease-a"));

    // when
    final List<Long> visitedFor10 = collectJobKeys(v -> state.visitByJobKey(10L, v));
    final List<Long> visitedFor20 = collectJobKeys(v -> state.visitByJobKey(20L, v));

    // then
    assertThat(visitedFor10).hasSize(1);
    assertThat(visitedFor20).hasSize(1);
  }

  @Test
  void shouldPutAndGetCommittedHistoryItemKey() {
    // given / when
    state.putCommittedHistoryItemKey(1L, "history-item-1", 101L);

    // then
    assertThat(state.getCommittedHistoryItemKey(1L, "history-item-1")).isEqualTo(101L);
  }

  @Test
  void shouldReturnNullForUncommittedHistoryItemId() {
    // given / when / then
    assertThat(state.getCommittedHistoryItemKey(1L, "unknown")).isNull();
  }

  @Test
  void shouldDeleteCommittedHistoryItemKey() {
    // given
    state.putCommittedHistoryItemKey(1L, "history-item-1", 101L);
    state.putCommittedHistoryItemKey(1L, "history-item-2", 102L);

    // when
    state.deleteCommittedHistoryItemKey(1L, "history-item-1");

    // then — only the deleted id is gone
    assertThat(state.getCommittedHistoryItemKey(1L, "history-item-1")).isNull();
    assertThat(state.getCommittedHistoryItemKey(1L, "history-item-2")).isEqualTo(102L);
  }

  @Test
  void shouldIgnoreDeleteOfUnknownCommittedHistoryItemKey() {
    // given / when / then — must not throw
    state.deleteCommittedHistoryItemKey(1L, "unknown");
  }

  @Test
  void shouldNotDeleteCommittedHistoryItemKeyOfOtherAgentInstance() {
    // given
    state.putCommittedHistoryItemKey(1L, "history-item-1", 101L);
    state.putCommittedHistoryItemKey(2L, "history-item-1", 201L);

    // when
    state.deleteCommittedHistoryItemKey(1L, "history-item-1");

    // then
    assertThat(state.getCommittedHistoryItemKey(2L, "history-item-1")).isEqualTo(201L);
  }

  @Test
  void shouldVisitAllCommittedHistoryItemIds() {
    // given
    state.putCommittedHistoryItemKey(1L, "history-item-1", 101L);
    state.putCommittedHistoryItemKey(1L, "history-item-2", 102L);

    // when
    final List<String> visited = new ArrayList<>();
    state.visitCommittedHistoryItemIds(
        1L,
        id -> {
          visited.add(id);
          return true;
        });

    // then
    assertThat(visited).containsExactlyInAnyOrder("history-item-1", "history-item-2");
  }

  @Test
  void shouldStopVisitingCommittedHistoryItemIdsWhenVisitorReturnsFalse() {
    // given
    for (int i = 0; i < 5; i++) {
      state.putCommittedHistoryItemKey(1L, "history-item-" + i, i);
    }

    // when
    final List<String> visited = new ArrayList<>();
    state.visitCommittedHistoryItemIds(
        1L,
        id -> {
          visited.add(id);
          return visited.size() < 3;
        });

    // then
    assertThat(visited).hasSize(3);
  }

  @Test
  void shouldNotVisitCommittedHistoryItemIdsOfOtherAgentInstance() {
    // given
    state.putCommittedHistoryItemKey(1L, "history-item-1", 101L);
    state.putCommittedHistoryItemKey(2L, "history-item-1", 201L);

    // when
    final List<String> visited = new ArrayList<>();
    state.visitCommittedHistoryItemIds(
        1L,
        id -> {
          visited.add(id);
          return true;
        });

    // then
    assertThat(visited).containsExactly("history-item-1");
  }

  @Test
  void shouldMarkAndCheckMetricsAccumulated() {
    // given / when
    state.markMetricsAccumulated(1L, "history-item-1");

    // then
    assertThat(state.hasAccumulatedMetrics(1L, "history-item-1")).isTrue();
    assertThat(state.hasAccumulatedMetrics(1L, "unknown")).isFalse();
  }

  @Test
  void shouldDeleteMetricsAccumulatedId() {
    // given
    state.markMetricsAccumulated(1L, "history-item-1");
    state.markMetricsAccumulated(1L, "history-item-2");

    // when
    state.deleteMetricsAccumulatedId(1L, "history-item-1");

    // then — only the deleted id is gone
    assertThat(state.hasAccumulatedMetrics(1L, "history-item-1")).isFalse();
    assertThat(state.hasAccumulatedMetrics(1L, "history-item-2")).isTrue();
  }

  @Test
  void shouldIgnoreDeleteOfUnknownMetricsAccumulatedId() {
    // given / when / then — must not throw
    state.deleteMetricsAccumulatedId(1L, "unknown");
  }

  @Test
  void shouldStopVisitingMetricsAccumulatedHistoryItemIdsWhenVisitorReturnsFalse() {
    // given
    for (int i = 0; i < 5; i++) {
      state.markMetricsAccumulated(1L, "history-item-" + i);
    }

    // when
    final List<String> visited = new ArrayList<>();
    state.visitMetricsAccumulatedHistoryItemIds(
        1L,
        id -> {
          visited.add(id);
          return visited.size() < 3;
        });

    // then
    assertThat(visited).hasSize(3);
  }

  @Test
  void shouldNotVisitMetricsAccumulatedHistoryItemIdsOfOtherAgentInstance() {
    // given
    state.markMetricsAccumulated(1L, "history-item-1");
    state.markMetricsAccumulated(2L, "history-item-1");

    // when
    final List<String> visited = new ArrayList<>();
    state.visitMetricsAccumulatedHistoryItemIds(
        1L,
        id -> {
          visited.add(id);
          return true;
        });

    // then
    assertThat(visited).containsExactly("history-item-1");
  }

  private static AgentHistoryRecord sampleRecord(final long jobKey, final String lease) {
    return new AgentHistoryRecord()
        .setJobKey(jobKey)
        .setJobLease(lease)
        .setAgentHistoryKey(jobKey * 1000L);
  }

  private List<Long> collectJobKeys(final java.util.function.Consumer<AgentHistoryVisitor> fn) {
    final List<Long> keys = new ArrayList<>();
    fn.accept(item -> keys.add(item.getJobKey()));
    return keys;
  }
}
