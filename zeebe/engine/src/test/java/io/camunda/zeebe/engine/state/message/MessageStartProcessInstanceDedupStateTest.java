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

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.metrics.MessageCorrelationMetricsDoc;
import io.camunda.zeebe.engine.state.immutable.MessageStartProcessInstanceDedupState.ExpiredEntryVisitor;
import io.camunda.zeebe.engine.state.mutable.MutableMessageStartProcessInstanceDedupState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class MessageStartProcessInstanceDedupStateTest {

  private static final String DEDUP_GAUGE =
      MessageCorrelationMetricsDoc.CROSS_PARTITION_DEDUP_ENTRIES.getName();

  private ZeebeDb<ZbColumnFamilies> zeebeDb;
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
    final ExpiredEntryVisitor collector =
        (pdk, mk) -> {
          visited.add(new long[] {pdk, mk});
          return true;
        };
    final var hasMore = state.visitExpiredEntries(5_000L, collector);

    // then — `deletionDeadline <= now` includes the entry at exactly now
    assertThat(hasMore).isFalse();
    assertThat(visited)
        .extracting(pair -> tuple(pair[0], pair[1]))
        .containsExactlyInAnyOrder(tuple(10L, 20L), tuple(10L, 22L));
  }

  @Test
  void shouldReportHasMoreWhenVisitorStopsEarly() {
    // given — three past-deadline entries
    state.put(10L, 20L, 30L, 1_000L);
    state.put(10L, 21L, 31L, 1_000L);
    state.put(10L, 22L, 32L, 1_000L);

    // when — visitor stops after the first entry
    final var visited = new ArrayList<long[]>();
    final var hasMore =
        state.visitExpiredEntries(
            5_000L,
            (pdk, mk) -> {
              visited.add(new long[] {pdk, mk});
              return false;
            });

    // then
    assertThat(visited).hasSize(1);
    assertThat(hasMore).isTrue();
  }

  @Test
  void shouldReportNoHasMoreWhenVisitorConsumesAllEntries() {
    // given
    state.put(10L, 20L, 30L, 1_000L);
    state.put(10L, 21L, 31L, 1_000L);

    // when
    final var hasMore = state.visitExpiredEntries(5_000L, (pdk, mk) -> true);

    // then
    assertThat(hasMore).isFalse();
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

  @Test
  void shouldTrackDedupEntriesGaugeAcrossPutAndDelete() {
    // when two distinct dedup entries are stored
    state.put(10L, 20L, 100L, 1_000L);
    state.put(10L, 21L, 101L, 2_000L);

    // then the gauge reflects both
    assertThat(dedupEntriesGauge()).isEqualTo(2.0);

    // when one is deleted
    state.delete(10L, 20L);

    // then the gauge drops to one
    assertThat(dedupEntriesGauge()).isEqualTo(1.0);
  }

  @Test
  void shouldNotDoubleCountDedupEntriesGaugeOnReReply() {
    // given a stored dedup entry
    state.put(10L, 20L, 100L, 1_000L);

    // when a fresh STARTED reply overwrites the same key (put upserts)
    state.put(10L, 20L, 101L, 5_000L);

    // then the gauge counts the entry only once
    assertThat(dedupEntriesGauge()).isEqualTo(1.0);
  }

  @Test
  void shouldNotDecrementDedupEntriesGaugeWhenDeletingAbsentEntry() {
    // given one stored entry
    state.put(10L, 20L, 100L, 1_000L);

    // when deleting an entry that was never stored
    state.delete(10L, 21L);

    // then the gauge is unaffected
    assertThat(dedupEntriesGauge()).isEqualTo(1.0);
  }

  @Test
  void shouldReseedDedupEntriesGaugeFromStateOnRecovery() {
    // given two persisted dedup entries
    state.put(10L, 20L, 100L, 1_000L);
    state.put(10L, 21L, 101L, 2_000L);

    // when the partition recovers
    ((DbMessageStartProcessInstanceDedupState) state).onRecovered(null);

    // then the gauge is authoritatively seeded from the persisted count
    assertThat(dedupEntriesGauge()).isEqualTo(2.0);
  }

  private double dedupEntriesGauge() {
    return zeebeDb.getMeterRegistry().get(DEDUP_GAUGE).gauge().value();
  }
}
