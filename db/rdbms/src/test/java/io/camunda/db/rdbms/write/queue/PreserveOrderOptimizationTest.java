/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.write.RdbmsWriterMetrics;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.TransactionIsolationLevel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Benchmarks the cost of the preserveOrder queue optimization as part of a full {@link
 * DefaultExecutionQueue#flush()} pass. Creates queue items with varying context types
 * (preserveOrder=true vs false), enqueues them through the public {@link
 * DefaultExecutionQueue#executeInQueue} API, and times flush() against a mocked MyBatis session so
 * no real SQL runs.
 */
@Tag("performance")
class PreserveOrderOptimizationTest {

  private static final Logger LOG =
      LoggerFactory.getLogger(PreserveOrderOptimizationTest.class.getName());

  private static final int WARMUP_ITERATIONS = 5;
  private static final int MEASUREMENT_ITERATIONS = 10;

  static Stream<Arguments> configurations() {
    return Stream.of(
        Arguments.of(1000, "pure_preserve_order"),
        Arguments.of(1000, "pure_no_preserve_order"),
        Arguments.of(1000, "mixed"),
        Arguments.of(10000, "pure_preserve_order"),
        Arguments.of(10000, "pure_no_preserve_order"),
        Arguments.of(10000, "mixed"),
        Arguments.of(100000, "pure_preserve_order"),
        Arguments.of(100000, "pure_no_preserve_order"),
        Arguments.of(100000, "mixed"));
  }

  @ParameterizedTest
  @MethodSource("configurations")
  void shouldMeasureFlushCost(final int queueSize, final String composition) throws Exception {
    final DefaultExecutionQueue queue = newQueueWithMockedSession();
    final List<QueueItem> items = buildQueue(queueSize, composition);

    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      items.forEach(queue::executeInQueue);
      queue.flush();
    }

    long totalNanos = 0;
    for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
      items.forEach(queue::executeInQueue);

      final long start = System.nanoTime();
      final int flushedCount = queue.flush();
      totalNanos += System.nanoTime() - start;

      assertThat(flushedCount).isEqualTo(items.size());
    }

    final double avgMicros = totalNanos / (MEASUREMENT_ITERATIONS * 1000.0);
    LOG.info(
        "flush(): N={}, composition={}, avg={} µs",
        queueSize,
        composition,
        String.format("%.1f", avgMicros));
  }

  private static DefaultExecutionQueue newQueueWithMockedSession() throws Exception {
    final Connection connection = mock(Connection.class);
    when(connection.getAutoCommit()).thenReturn(false);

    final SqlSession session = mock(SqlSession.class);
    when(session.getConnection()).thenReturn(connection);
    when(session.flushStatements()).thenReturn(List.<BatchResult>of());

    final SqlSessionFactory sessionFactory = mock(SqlSessionFactory.class);
    when(sessionFactory.openSession(any(ExecutorType.class), any(TransactionIsolationLevel.class)))
        .thenReturn(session);

    return new DefaultExecutionQueue(
        sessionFactory, 1, Integer.MAX_VALUE, 0, mock(RdbmsWriterMetrics.class));
  }

  private static List<QueueItem> buildQueue(final int size, final String composition) {
    final var items = new ArrayList<QueueItem>(size);
    final WriteStatementType[] types = WriteStatementType.values();
    final String[] statementIds = {
      "mapper.insert", "mapper.update", "mapper.delete", "mapper.upsert"
    };

    for (int i = 0; i < size; i++) {
      final ContextType contextType = pickContextType(composition, i);
      items.add(
          new QueueItem(
              contextType,
              types[i % types.length],
              (long) i,
              statementIds[i % statementIds.length],
              "param-" + i));
    }
    return items;
  }

  private static ContextType pickContextType(final String composition, final int index) {
    return switch (composition) {
      case "pure_preserve_order" -> ContextType.MESSAGE_SUBSCRIPTION;
      case "pure_no_preserve_order" -> ContextType.JOB;
      case "mixed" ->
          index % 3 == 0
              ? ContextType.MESSAGE_SUBSCRIPTION
              : index % 3 == 1 ? ContextType.PROCESS_INSTANCE : ContextType.JOB;
      default -> throw new IllegalArgumentException("Unknown composition: " + composition);
    };
  }
}
