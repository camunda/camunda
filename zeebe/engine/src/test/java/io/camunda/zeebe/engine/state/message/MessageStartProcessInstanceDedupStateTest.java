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

import io.camunda.zeebe.engine.state.immutable.MessageStartProcessInstanceDedupState.ExpiredEntryVisitor;
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
  void shouldStoreEntryWithDeletionDeadline() {
    // given
    state.put(10L, 20L, 30L, 5_000L);

    // when
    final var entry = state.get(10L, 20L);

    // then
    assertThat(entry).isNotNull();
    assertThat(entry.getProcessInstanceKey()).isEqualTo(30L);
    assertThat(entry.getDeletionDeadline()).isEqualTo(5_000L);
  }

  @Test
  void shouldKeyEntriesByProcessDefinitionAndMessageKey() {
    // given
    state.put(10L, 20L, 100L, 1_000L);
    state.put(10L, 21L, 101L, 2_000L);
    state.put(11L, 20L, 102L, 3_000L);

    // when / then
    assertThat(state.get(10L, 20L).getProcessInstanceKey()).isEqualTo(100L);
    assertThat(state.get(10L, 21L).getProcessInstanceKey()).isEqualTo(101L);
    assertThat(state.get(11L, 20L).getProcessInstanceKey()).isEqualTo(102L);
  }

  @Test
  void shouldOverwriteExistingEntryOnSameKey() {
    // given — a fresh STARTED reply for a re-claimed (processDefinitionKey, messageKey)
    state.put(10L, 20L, 30L, 1_000L);

    // when
    state.put(10L, 20L, 31L, 5_000L);

    // then
    final var entry = state.get(10L, 20L);
    assertThat(entry.getProcessInstanceKey()).isEqualTo(31L);
    assertThat(entry.getDeletionDeadline()).isEqualTo(5_000L);
  }

  @Test
  void shouldVisitOnlyEntriesPastTheirDeletionDeadline() {
    // given
    state.put(10L, 20L, 30L, 1_000L); // past
    state.put(10L, 21L, 31L, 9_000L); // future
    state.put(10L, 22L, 32L, 5_000L); // exactly at probed `now`

    // when
    final var visited = new ArrayList<long[]>();
    final ExpiredEntryVisitor collector = (pdk, mk) -> visited.add(new long[] {pdk, mk});
    state.visitExpiredEntries(5_000L, collector);

    // then — `deletionDeadline <= now` includes the entry at exactly now
    assertThat(visited)
        .extracting(pair -> tuple(pair[0], pair[1]))
        .containsExactlyInAnyOrder(tuple(10L, 20L), tuple(10L, 22L));
  }

  @Test
  void shouldReportNoExpiredEntryWhenAllAreInTheFuture() {
    // given
    state.put(10L, 20L, 30L, 9_000L);

    // when / then
    assertThat(state.hasExpiredEntry(5_000L)).isFalse();
  }

  @Test
  void shouldReportExpiredEntryWhenAtLeastOneIsPastDeadline() {
    // given
    state.put(10L, 20L, 30L, 1_000L);
    state.put(10L, 21L, 31L, 9_000L);

    // when / then
    assertThat(state.hasExpiredEntry(5_000L)).isTrue();
  }

  @Test
  void shouldAllowVisitorToDeleteWhileIterating() {
    // given
    state.put(10L, 20L, 30L, 1_000L);
    state.put(10L, 21L, 31L, 1_000L);

    // when
    final List<long[]> visited = new ArrayList<>();
    state.visitExpiredEntries(
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
  void shouldDeleteEntry() {
    // given
    state.put(10L, 20L, 30L, 5_000L);

    // when
    state.delete(10L, 20L);

    // then
    assertThat(state.get(10L, 20L)).isNull();
  }

  @Test
  void shouldTreatDeleteOfUnknownEntryAsNoOp() {
    // when / then — must not throw
    state.delete(10L, 20L);
  }
}
