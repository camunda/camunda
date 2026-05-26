/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.state.immutable.MessageStartProcessInstanceDedupState.TombstoneVisitor;
import io.camunda.zeebe.engine.state.mutable.MutableMessageStartProcessInstanceDedupState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class MessageStartProcessInstanceDedupStateTest {

  private MutableProcessingState processingState;
  private MutableMessageStartProcessInstanceDedupState state;

  @BeforeEach
  void setUp() {
    state = processingState.getMessageStartProcessInstanceDedupState();
  }

  @Test
  void shouldReturnNullWhenNoEntryExists() {
    // when / then
    assertThat(state.get(1L, 2L)).isNull();
  }

  @Test
  void shouldStoreActiveEntry() {
    // given
    state.putActive(10L, 20L, 30L);

    // when
    final var entry = state.get(10L, 20L);

    // then
    assertThat(entry).isNotNull();
    assertThat(entry.getProcessInstanceKey()).isEqualTo(30L);
    assertThat(entry.getStatus()).isEqualTo(MessageStartProcessInstanceDedupStatus.ACTIVE);
    assertThat(entry.getDeletionDeadline()).isEqualTo(-1L);
  }

  @Test
  void shouldKeyEntriesByProcessDefinitionAndMessageKey() {
    // given
    state.putActive(10L, 20L, 100L);
    state.putActive(10L, 21L, 101L);
    state.putActive(11L, 20L, 102L);

    // when / then
    assertThat(state.get(10L, 20L).getProcessInstanceKey()).isEqualTo(100L);
    assertThat(state.get(10L, 21L).getProcessInstanceKey()).isEqualTo(101L);
    assertThat(state.get(11L, 20L).getProcessInstanceKey()).isEqualTo(102L);
  }

  @Test
  void shouldTombstoneEntryByProcessInstanceKey() {
    // given
    state.putActive(10L, 20L, 30L);

    // when
    state.tombstoneByProcessInstanceKey(30L, 5_000L);

    // then
    final var entry = state.get(10L, 20L);
    assertThat(entry.getStatus()).isEqualTo(MessageStartProcessInstanceDedupStatus.TOMBSTONE);
    assertThat(entry.getDeletionDeadline()).isEqualTo(5_000L);
    assertThat(entry.getProcessInstanceKey()).isEqualTo(30L);
  }

  @Test
  void shouldIgnoreTombstoneRequestForUnknownProcessInstance() {
    // when / then — must not throw
    state.tombstoneByProcessInstanceKey(999L, 5_000L);
  }

  @Test
  void shouldVisitOnlyTombstonesPastDeadline() {
    // given
    state.putActive(10L, 20L, 30L);
    state.putActive(10L, 21L, 31L);
    state.putActive(10L, 22L, 32L);
    state.tombstoneByProcessInstanceKey(30L, 1_000L); // past
    state.tombstoneByProcessInstanceKey(31L, 9_000L); // future
    // PI 32 stays ACTIVE

    // when
    final var visited = new ArrayList<long[]>();
    final TombstoneVisitor collector = (pdk, mk) -> visited.add(new long[] {pdk, mk});
    state.visitTombstonesPastDeadline(5_000L, collector);

    // then
    assertThat(visited)
        .extracting(pair -> tuple(pair[0], pair[1]))
        .containsExactlyInAnyOrder(tuple(10L, 20L));
  }

  @Test
  void shouldAllowVisitorToDeleteWhileIterating() {
    // given
    state.putActive(10L, 20L, 30L);
    state.putActive(10L, 21L, 31L);
    state.tombstoneByProcessInstanceKey(30L, 1_000L);
    state.tombstoneByProcessInstanceKey(31L, 1_000L);

    // when
    final List<long[]> visited = new ArrayList<>();
    state.visitTombstonesPastDeadline(
        5_000L,
        (pdk, mk) -> {
          visited.add(new long[] {pdk, mk});
          state.delete(pdk, mk);
        });

    // then
    assertThat(visited).hasSize(2);
    assertThat(state.get(10L, 20L)).isNull();
    assertThat(state.get(10L, 21L)).isNull();
  }

  @Test
  void shouldDeleteRemovesBothForwardAndReverseEntries() {
    // given
    state.putActive(10L, 20L, 30L);

    // when
    state.delete(10L, 20L);

    // then
    assertThat(state.get(10L, 20L)).isNull();
    // reverse mapping is also gone — a later tombstone-by-PI call must be a no-op
    state.tombstoneByProcessInstanceKey(30L, 5_000L);
    assertThat(state.get(10L, 20L)).isNull();
  }

  @Test
  void shouldTreatDeleteOfUnknownEntryAsNoOp() {
    // when / then — must not throw
    state.delete(10L, 20L);
  }
}
