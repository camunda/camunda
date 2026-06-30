/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.analytics.fact.ProcessInstanceExecutionTimeFact;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ExecutionTimeAggregatorTest {

  private static final long DEF_KEY = 77L;
  private static final int VERSION = 3;
  private static final String TENANT = "<default>";

  private ExecutionTimeAggregator aggregator;

  @BeforeEach
  void setUp() {
    final JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:exec-agg-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    aggregator = new ExecutionTimeAggregator(dataSource);
    aggregator.initSchema();
  }

  @Test
  void shouldInsertAggregateForFirstFact() {
    // when
    final boolean applied = aggregator.apply(fact(500L, 1, 11L));

    // then
    assertThat(applied).isTrue();
    final ExecutionTimeAggregate agg = aggregator.read(DEF_KEY, VERSION, TENANT).orElseThrow();
    assertThat(agg.instanceCount()).isEqualTo(1L);
    assertThat(agg.totalDurationMs()).isEqualTo(500L);
    assertThat(agg.minDurationMs()).isEqualTo(500L);
    assertThat(agg.maxDurationMs()).isEqualTo(500L);
    assertThat(agg.averageDurationMs()).isEqualTo(500.0);
    assertThat(agg.bpmnProcessId()).isEqualTo("order");
  }

  @Test
  void shouldAccumulateMultipleFactsForSameDefinition() {
    // when
    aggregator.apply(fact(500L, 1, 11L));
    aggregator.apply(fact(300L, 1, 12L));
    aggregator.apply(fact(700L, 1, 13L));

    // then
    final ExecutionTimeAggregate agg = aggregator.read(DEF_KEY, VERSION, TENANT).orElseThrow();
    assertThat(agg.instanceCount()).isEqualTo(3L);
    assertThat(agg.totalDurationMs()).isEqualTo(1500L);
    assertThat(agg.minDurationMs()).isEqualTo(300L);
    assertThat(agg.maxDurationMs()).isEqualTo(700L);
    assertThat(agg.averageDurationMs()).isEqualTo(500.0);
  }

  @Test
  void shouldDedupDuplicateFactBySourceCoordinate() {
    // given
    assertThat(aggregator.apply(fact(500L, 1, 11L))).isTrue();

    // when — the same fact is redelivered (same source partition + position)
    final boolean reapplied = aggregator.apply(fact(500L, 1, 11L));

    // then — dropped, not double-counted
    assertThat(reapplied).isFalse();
    assertThat(aggregator.read(DEF_KEY, VERSION, TENANT).orElseThrow().instanceCount())
        .isEqualTo(1L);
  }

  @Test
  void shouldDedupAnyFactAtOrBelowTheWatermark() {
    // given — watermark for partition 1 advanced to position 13
    aggregator.apply(fact(500L, 1, 11L));
    aggregator.apply(fact(300L, 1, 13L));

    // when — a fact below the watermark arrives (e.g. re-emitted tail after failover)
    final boolean reapplied = aggregator.apply(fact(900L, 1, 12L));

    // then
    assertThat(reapplied).isFalse();
    assertThat(aggregator.read(DEF_KEY, VERSION, TENANT).orElseThrow().instanceCount())
        .isEqualTo(2L);
  }

  @Test
  void shouldTrackWatermarkPerSourcePartition() {
    // given — partition 1 advanced to position 11
    aggregator.apply(fact(500L, 1, 11L));

    // when — a lower position but from a different source partition
    final boolean applied = aggregator.apply(fact(300L, 2, 5L));

    // then — independent watermark, so it is aggregated
    assertThat(applied).isTrue();
    assertThat(aggregator.read(DEF_KEY, VERSION, TENANT).orElseThrow().instanceCount())
        .isEqualTo(2L);
  }

  private static ProcessInstanceExecutionTimeFact fact(
      final long durationMs, final int sourcePartitionId, final long sourcePosition) {
    return new ProcessInstanceExecutionTimeFact(
        1L,
        DEF_KEY,
        "order",
        VERSION,
        TENANT,
        1000L,
        1000L + durationMs,
        durationMs,
        true,
        sourcePartitionId,
        sourcePosition,
        java.util.Map.of());
  }
}
