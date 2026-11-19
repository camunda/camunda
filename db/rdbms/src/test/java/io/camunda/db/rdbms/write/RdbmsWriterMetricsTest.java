/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.RdbmsWriterMetrics.FlushTrigger;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RdbmsWriterMetricsTest {

  private static final String PREFIX = "zeebe.rdbms.exporter.";
  private static final String SQL_PACKAGE = "io.camunda.db.rdbms.sql.";

  private MeterRegistry registry;
  private RdbmsWriterMetrics metrics;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    metrics = new RdbmsWriterMetrics(registry);
  }

  @Test
  void shouldCountEnqueuedStatementsPerStatementId() {
    // given
    final var first = SQL_PACKAGE + "ProcessInstanceMapper.insert";
    final var second = "CustomStatement";

    // when
    metrics.recordEnqueuedStatement(first);
    metrics.recordEnqueuedStatement(first);
    metrics.recordEnqueuedStatement(second);

    // then
    final var firstCounter =
        registry
            .get(PREFIX + "enqueued.statements")
            .tag("statementId", "ProcessInstanceMapper.insert")
            .counter();
    assertThat(firstCounter.count()).isEqualTo(2);
    assertThat(firstCounter.getId().getDescription()).isEqualTo("Number of enqueued statements");
    assertThat(
            registry
                .get(PREFIX + "enqueued.statements")
                .tag("statementId", "CustomStatement")
                .counter()
                .count())
        .isEqualTo(1);
    assertThat(registry.get(PREFIX + "enqueued.statements").counters()).hasSize(2);
  }

  @Test
  void shouldCountMergedQueueItemsPerContextTypeAndStatementId() {
    // given
    final var statement = SQL_PACKAGE + "FlowNodeInstanceMapper.update";
    final var other = SQL_PACKAGE + "VariableMapper.insert";
    final var contextA = ContextType.values()[0];
    final var contextB = ContextType.values()[1];

    // when
    metrics.recordMergedQueueItem(contextA, statement);
    metrics.recordMergedQueueItem(contextA, statement);
    metrics.recordMergedQueueItem(contextB, statement);
    metrics.recordMergedQueueItem(contextB, other);

    // then
    assertThat(mergedCount(contextA, "FlowNodeInstanceMapper.update")).isEqualTo(2);
    assertThat(mergedCount(contextB, "FlowNodeInstanceMapper.update")).isEqualTo(1);
    assertThat(mergedCount(contextB, "VariableMapper.insert")).isEqualTo(1);
    assertThat(registry.get(PREFIX + "merged.queue.item").counters()).hasSize(3);
  }

  @Test
  void shouldRecordExecutedStatementMeters() {
    // given
    final var statement = SQL_PACKAGE + "JobMapper.insert";
    final var other = SQL_PACKAGE + "JobMapper.update";

    // when
    metrics.recordExecutedStatement(statement, 4);
    metrics.recordExecutedStatement(statement, 2);
    metrics.recordExecutedStatement(other, 1);

    // then
    assertThat(
            registry
                .get(PREFIX + "executed.statements")
                .tag("statementId", "JobMapper.insert")
                .counter()
                .count())
        .isEqualTo(2);
    assertThat(
            registry
                .get(PREFIX + "executed.queue.item")
                .tag("statementId", "JobMapper.insert")
                .counter()
                .count())
        .isEqualTo(6);
    assertThat(
            registry
                .get(PREFIX + "executed.queue.item")
                .tag("statementId", "JobMapper.update")
                .counter()
                .count())
        .isEqualTo(1);

    final var batches =
        registry.get(PREFIX + "num.batches").tag("statementId", "JobMapper.insert").summary();
    assertThat(batches.count()).isEqualTo(2);
    assertThat(batches.totalAmount()).isEqualTo(125.0);
    assertThat(registry.get(PREFIX + "num.batches").summaries()).hasSize(2);
  }

  @Test
  void shouldCountQueueFlushesPerTrigger() {
    // when
    metrics.recordQueueFlush(FlushTrigger.COUNT_LIMIT);
    metrics.recordQueueFlush(FlushTrigger.COUNT_LIMIT);
    metrics.recordQueueFlush(FlushTrigger.SHUTDOWN);

    // then
    assertThat(registry.get(PREFIX + "flush").tag("trigger", "COUNT_LIMIT").counter().count())
        .isEqualTo(2);
    assertThat(registry.get(PREFIX + "flush").tag("trigger", "SHUTDOWN").counter().count())
        .isEqualTo(1);
    assertThat(registry.get(PREFIX + "flush").counters()).hasSize(2);
  }

  @Test
  void shouldRecordBulkSizeAndFailedFlush() {
    // when
    metrics.recordBulkSize(10);
    metrics.recordBulkSize(30);
    metrics.recordFailedFlush();
    metrics.recordFailedFlush();

    // then
    final var bulk = registry.get(PREFIX + "bulk.size").summary();
    assertThat(bulk.count()).isEqualTo(2);
    assertThat(bulk.totalAmount()).isEqualTo(40.0);
    assertThat(bulk.getId().getDescription()).isEqualTo("Exporter bulk size");
    assertThat(registry.get(PREFIX + "failed.flush").counter().count()).isEqualTo(2);
  }

  @Test
  void shouldMeasureDurationsWithTimers() {
    // when
    try (final var ignored = metrics.measureFlushDuration()) {
      // measured section
    }
    try (final var ignored = metrics.measureFlushDuration()) {
      // measured section
    }
    try (final var ignored = metrics.measureHistoryCleanupDuration()) {
      // measured section
    }

    // then
    assertThat(registry.get(PREFIX + "flush.duration.seconds").timer().count()).isEqualTo(2);
    assertThat(registry.get(PREFIX + "historyCleanup.duration.seconds").timer().count())
        .isEqualTo(1);
  }

  @Test
  void shouldRecordHistoryCleanupEntities() {
    // when
    metrics.recordHistoryCleanupEntities(5, "PROCESS_INSTANCE");
    metrics.recordHistoryCleanupEntities(7, "PROCESS_INSTANCE");
    metrics.recordHistoryCleanupEntities(3, "VARIABLE");

    // then
    assertThat(
            registry
                .get(PREFIX + "historyCleanup.deletedEntities")
                .tag("entity", "PROCESS_INSTANCE")
                .counter()
                .count())
        .isEqualTo(12);
    assertThat(
            registry
                .get(PREFIX + "historyCleanup.bulk.size")
                .tag("entity", "VARIABLE")
                .summary()
                .totalAmount())
        .isEqualTo(3.0);
  }

  private double mergedCount(final ContextType contextType, final String statementId) {
    return registry
        .get(PREFIX + "merged.queue.item")
        .tag("contextType", contextType.name())
        .tag("statementId", statementId)
        .counter()
        .count();
  }
}
