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
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class WindowedExecutionTimeAggregatorTest {

  private static final long HOUR = 3_600_000L;
  private static final long LATENESS = 60_000L;
  private static final int VERSION = 1;
  private static final String TENANT = "<default>";
  private static final long DS = 1L;
  private static final List<AggregateDataset> HOURLY = List.of(new AggregateDataset(DS, HOUR));

  private WindowedExecutionTimeAggregator aggregator;

  @BeforeEach
  void setUp() {
    final JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:win-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    aggregator = new WindowedExecutionTimeAggregator(dataSource, LATENESS);
    aggregator.initSchema();
  }

  @Test
  void shouldCountCompletionsPerDefinitionPerHour() {
    // given — definition 77: two completions in hour 0, one in hour 1; definition 88: one in hour 0
    aggregator.apply(fact(77L, 1_000L, 500L, 1, 10L), HOURLY); // hour 0
    aggregator.apply(fact(77L, 2_000L, 300L, 1, 11L), HOURLY); // hour 0
    aggregator.apply(fact(77L, HOUR + 1_000L, 700L, 1, 12L), HOURLY); // hour 1
    aggregator.apply(fact(88L, 500L, 900L, 1, 13L), HOURLY); // hour 0

    // then — "N completed per definition per hour"
    assertThat(count(77L, 0L)).isEqualTo(2L);
    assertThat(count(77L, HOUR)).isEqualTo(1L);
    assertThat(count(88L, 0L)).isEqualTo(1L);

    // and — windowed duration stats roll up within the cell
    final WindowedExecutionTime hour0 = aggregator.read(DS, 77L, VERSION, TENANT, 0L).orElseThrow();
    assertThat(hour0.totalDurationMs()).isEqualTo(800L);
    assertThat(hour0.minDurationMs()).isEqualTo(300L);
    assertThat(hour0.maxDurationMs()).isEqualTo(500L);
    assertThat(hour0.windowEnd()).isEqualTo(HOUR);
  }

  @Test
  void shouldAggregateSameFactIntoEachDatasetWithItsOwnWindow() {
    // given — two datasets over the same fact: hourly (id 1) and daily (id 2)
    final long day = 86_400_000L;
    final List<AggregateDataset> both =
        List.of(new AggregateDataset(DS, HOUR), new AggregateDataset(2L, day));

    // when — one completion folds into both
    aggregator.apply(fact(77L, HOUR + 1_000L, 500L, 1, 10L), both);

    // then — hourly buckets at HOUR, daily buckets at 0; each its own row
    assertThat(aggregator.read(DS, 77L, VERSION, TENANT, HOUR).orElseThrow().completedCount())
        .isEqualTo(1L);
    assertThat(aggregator.read(2L, 77L, VERSION, TENANT, 0L).orElseThrow().completedCount())
        .isEqualTo(1L);
  }

  @Test
  void shouldDedupDuplicateFactBySourceCoordinate() {
    // given
    assertThat(aggregator.apply(fact(77L, 1_000L, 500L, 1, 10L), HOURLY)).isTrue();

    // when — same source coordinate redelivered
    final boolean reapplied = aggregator.apply(fact(77L, 1_000L, 500L, 1, 10L), HOURLY);

    // then
    assertThat(reapplied).isFalse();
    assertThat(count(77L, 0L)).isEqualTo(1L);
  }

  @Test
  void shouldFinalizeWindowOnceEventTimeWatermarkPasses() {
    // given — one completion in hour 0; watermark is still inside hour 0
    aggregator.apply(fact(77L, 1_000L, 500L, 1, 10L), HOURLY);
    assertThat(aggregator.read(DS, 77L, VERSION, TENANT, 0L).orElseThrow().finalized()).isFalse();

    // when — a later completion advances the event-time watermark past hour 0's end + lateness
    aggregator.apply(fact(99L, HOUR + LATENESS + 1L, 100L, 1, 11L), HOURLY);

    // then — hour 0 is now final, the in-progress later window is not
    assertThat(aggregator.read(DS, 77L, VERSION, TENANT, 0L).orElseThrow().finalized()).isTrue();
    assertThat(aggregator.read(DS, 99L, VERSION, TENANT, HOUR).orElseThrow().finalized()).isFalse();
  }

  private long count(final long processDefinitionKey, final long windowStart) {
    return aggregator
        .read(DS, processDefinitionKey, VERSION, TENANT, windowStart)
        .orElseThrow()
        .completedCount();
  }

  private static ProcessInstanceExecutionTimeFact fact(
      final long processDefinitionKey,
      final long endTime,
      final long durationMs,
      final int sourcePartitionId,
      final long sourcePosition) {
    return new ProcessInstanceExecutionTimeFact(
        endTime, // processInstanceKey (arbitrary, unique enough per fact here)
        processDefinitionKey,
        "order",
        VERSION,
        TENANT,
        endTime - durationMs,
        endTime,
        durationMs,
        true,
        sourcePartitionId,
        sourcePosition);
  }
}
