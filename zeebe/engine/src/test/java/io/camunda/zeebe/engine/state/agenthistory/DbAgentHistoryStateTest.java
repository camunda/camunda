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
