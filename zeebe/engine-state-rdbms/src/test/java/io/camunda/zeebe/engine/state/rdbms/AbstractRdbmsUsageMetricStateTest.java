/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import io.camunda.zeebe.engine.state.metrics.AbstractUsageMetricStateContractTest;
import io.camunda.zeebe.engine.state.metrics.HashUtil;
import io.camunda.zeebe.engine.state.metrics.PersistedUsageMetrics;
import io.camunda.zeebe.engine.state.metrics.UsageMetricStateImpl;
import io.camunda.zeebe.engine.state.mutable.MutableUsageMetricState;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Runs the usage metric state contract against PostgreSQL, plus the tests for what is specific to
 * the relational store: the rows, rollback, and the bucket across transactions.
 *
 * <p>All tests share one {@link EngineStateDatabase}; each test writes to its own partition.
 */
abstract class AbstractRdbmsUsageMetricStateTest extends AbstractUsageMetricStateContractTest {

  /** How the session under test writes: directly, or behind the commit. */
  abstract StateMode mode();

  private static EngineStateDatabase database;

  private JdbcTransactionContext transactionContext;
  private int partitionId;
  private MutableUsageMetricState state;

  @BeforeAll
  static void startDatabase() throws Exception {
    database = EngineStateDatabase.start();
  }

  @AfterAll
  static void stopDatabase() {
    database.close();
  }

  @Override
  protected MutableUsageMetricState createUsageMetricState() {
    transactionContext = new JdbcTransactionContext();
    partitionId = database.nextPartitionId();
    final var session = database.entitySession(mode(), transactionContext, partitionId);
    state = new UsageMetricStateImpl(new RdbmsUsageMetricStore(session));
    return state;
  }

  @AfterEach
  void endTransaction() throws Exception {
    transactionContext.getCurrentTransaction().rollback();
  }

  @Test
  void shouldWriteRowsOnCommit() throws Exception {
    // given
    final var transaction = transactionContext.getCurrentTransaction();
    state.resetActiveBucket(7L);
    state.recordRPIMetric("tenant1");
    state.recordRPIMetric("tenant1");
    state.recordEDIMetric("tenant1");
    state.recordTUMetric("tenant1", "assignee1");
    state.recordTUMetric("tenant1", "assignee1");
    state.recordTUMetric("tenant2", "assignee1");
    assertThat(usageMetricRows()).isEmpty();

    // when
    transaction.commit();

    // then
    final long assigneeHash = HashUtil.getStringHashValue("assignee1");
    assertThat(usageMetricRows())
        .containsExactlyInAnyOrder(
            tuple(1, "tenant1", 0L, 2L),
            tuple(2, "tenant1", 0L, 1L),
            tuple(3, "tenant1", assigneeHash, 1L),
            tuple(3, "tenant2", assigneeHash, 1L));
    assertThat(
            rows(
                "SELECT value FROM CAM_RU_PARTITION_STATE WHERE state_key = 'usage-metrics.from-time'"))
        .containsExactly(tuple("7"));
  }

  @Test
  void shouldLeaveNothingOnRollback() throws Exception {
    // given
    final var transaction = transactionContext.getCurrentTransaction();
    state.resetActiveBucket(7L);
    state.recordRPIMetric("tenant1");

    // when
    transaction.rollback();

    // then
    assertThat(state.getActiveBucket()).isNull();
    assertThat(usageMetricRows()).isEmpty();
  }

  @Test
  void shouldIncrementCommittedCountersAndRecognizeCommittedAssignees() throws Exception {
    // given
    final var first = transactionContext.getCurrentTransaction();
    state.resetActiveBucket(7L);
    state.recordRPIMetric("tenant1");
    state.recordTUMetric("tenant1", "assignee1");
    first.commit();

    // when
    final var second = transactionContext.getCurrentTransaction();
    state.recordRPIMetric("tenant1");
    state.recordTUMetric("tenant1", "assignee1");
    state.recordTUMetric("tenant1", "assignee2");
    second.commit();

    // then
    final PersistedUsageMetrics bucket = state.getActiveBucket();
    assertThat(bucket.getFromTime()).isEqualTo(7L);
    assertThat(bucket.getTenantRPIMap()).isEqualTo(Map.of("tenant1", 2L));
    assertThat(bucket.getTenantTUMap().get("tenant1")).hasSize(2);
  }

  @Test
  void shouldDeleteCommittedMetricsOnResetAndExport() throws Exception {
    // given - a committed bucket
    final var first = transactionContext.getCurrentTransaction();
    state.resetActiveBucket(7L);
    state.recordRPIMetric("tenant1");
    state.recordEDIMetric("tenant2");
    state.recordTUMetric("tenant1", "assignee1");
    first.commit();

    // when - the export reads the bucket and its follow-up event resets it
    final var second = transactionContext.getCurrentTransaction();
    final var exported = state.getActiveBucket().close(10L);
    state.resetActiveBucket(10L);
    state.recordRPIMetric("tenant3");
    second.commit();

    // then
    assertThat(exported.getFromTime()).isEqualTo(7L);
    assertThat(exported.getToTime()).isEqualTo(10L);
    assertThat(exported.getTenantRPIMap()).isEqualTo(Map.of("tenant1", 1L));
    assertThat(exported.getTenantEDIMap()).isEqualTo(Map.of("tenant2", 1L));
    assertThat(usageMetricRows()).containsExactly(tuple(1, "tenant3", 0L, 1L));
    final var bucket = state.getActiveBucket();
    assertThat(bucket.getFromTime()).isEqualTo(10L);
    assertThat(bucket.getTenantEDIMap()).isEmpty();
    assertThat(bucket.getTenantTUMap()).isEmpty();
  }

  private List<Tuple> usageMetricRows() throws SQLException {
    return rows(
        "SELECT metric_type, tenant_id, assignee_hash, metric_count FROM CAM_RU_USAGE_METRIC");
  }

  /**
   * The committed rows of this test's partition, ordered by the first column, of a query of the
   * form {@code SELECT <columns> FROM <table>}, optionally with a condition.
   */
  private List<Tuple> rows(final String query) throws SQLException {
    final String sql =
        query.contains(" WHERE ")
            ? query.replace(" WHERE ", " WHERE partition_id = ? AND ") + " ORDER BY 1"
            : query + " WHERE partition_id = ? ORDER BY 1";
    final List<Tuple> rows = new ArrayList<>();
    try (final var connection = database.dataSource().getConnection();
        final var statement = connection.prepareStatement(sql)) {
      statement.setInt(1, partitionId);
      try (final ResultSet resultSet = statement.executeQuery()) {
        final int columns = resultSet.getMetaData().getColumnCount();
        while (resultSet.next()) {
          final Object[] values = new Object[columns];
          for (int column = 1; column <= columns; column++) {
            values[column - 1] = resultSet.getObject(column);
          }
          rows.add(tuple(values));
        }
      }
    }
    return rows;
  }
}
