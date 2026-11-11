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
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.jobmetrics.AbstractJobMetricsStateContractTest;
import io.camunda.zeebe.engine.state.jobmetrics.JobMetricsStateImpl;
import io.camunda.zeebe.engine.state.jobmetrics.StatusMetrics;
import io.camunda.zeebe.engine.state.mutable.MutableJobMetricsState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.JobMetricsExportState;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Runs the job metrics state contract against PostgreSQL, plus the tests for what is specific to
 * the relational store: the rows, rollback, and the batch across transactions, its export and its
 * clean-up.
 *
 * <p>All tests share one {@link EngineStateDatabase}; each test writes to its own partition.
 */
abstract class AbstractRdbmsJobMetricsStateTest extends AbstractJobMetricsStateContractTest {

  /** How the session under test writes: directly, or behind the commit. */
  abstract StateMode mode();

  private static EngineStateDatabase database;

  private JdbcTransactionContext transactionContext;
  private int partitionId;
  private MutableJobMetricsState state;

  @BeforeAll
  static void startDatabase() throws Exception {
    database = EngineStateDatabase.start();
  }

  @AfterAll
  static void stopDatabase() {
    database.close();
  }

  @Override
  protected MutableJobMetricsState createJobMetricsState() {
    return createJobMetricsState(new EngineConfiguration());
  }

  private MutableJobMetricsState createJobMetricsState(final EngineConfiguration config) {
    transactionContext = new JdbcTransactionContext();
    partitionId = database.nextPartitionId();
    final var session = database.entitySession(mode(), transactionContext, partitionId);
    state =
        new JobMetricsStateImpl(new RdbmsJobMetricsStore(session), InstantSource.system(), config);
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
    increment("type1", "tenant1", "worker1", JobMetricsExportState.CREATED);
    increment("type1", "tenant1", "worker1", JobMetricsExportState.CREATED);
    increment("type1", "tenant1", "worker1", JobMetricsExportState.COMPLETED);
    increment("type2", "tenant1", "worker1", JobMetricsExportState.FAILED);
    assertThat(jobMetricRows()).isEmpty();

    // when
    transaction.commit();

    // then
    assertThat(jobMetricRows())
        .containsExactly(
            tuple("type1", "tenant1", "worker1", 2, 1, 0),
            tuple("type2", "tenant1", "worker1", 0, 0, 1));
    assertThat(state.getBatchStartTime()).isPositive();
    assertThat(state.getBatchEndTime()).isGreaterThanOrEqualTo(state.getBatchStartTime());
    assertThat(rows("SELECT key_count FROM CAM_RU_JOB_METRIC_BATCH")).containsExactly(tuple(2));
  }

  @Test
  void shouldLeaveNothingOnRollback() throws Exception {
    // given
    final var transaction = transactionContext.getCurrentTransaction();
    increment("type1", "tenant1", "worker1", JobMetricsExportState.CREATED);

    // when
    transaction.rollback();

    // then
    assertThat(collect()).isEmpty();
    assertThat(state.getBatchStartTime()).isZero();
    assertThat(jobMetricRows()).isEmpty();
  }

  @Test
  void shouldIncrementCommittedMetrics() throws Exception {
    // given
    final var first = transactionContext.getCurrentTransaction();
    increment("type1", "tenant1", "worker1", JobMetricsExportState.CREATED);
    first.commit();
    final long startTime = state.getBatchStartTime();

    // when
    final var second = transactionContext.getCurrentTransaction();
    increment("type1", "tenant1", "worker1", JobMetricsExportState.CREATED);
    increment("type1", "tenant1", "worker1", JobMetricsExportState.COMPLETED);
    second.commit();

    // then
    assertThat(jobMetricRows()).containsExactly(tuple("type1", "tenant1", "worker1", 2, 1, 0));
    assertThat(state.getBatchStartTime()).isGreaterThanOrEqualTo(startTime);
  }

  @Test
  void shouldExportCommittedMetricsAndCleanThemUp() throws Exception {
    // given - a committed batch
    final var first = transactionContext.getCurrentTransaction();
    increment("type1", "tenant1", "worker1", JobMetricsExportState.CREATED);
    increment("type2", "tenant2", "worker2", JobMetricsExportState.FAILED);
    first.commit();

    // when - the export reads the batch and its follow-up event cleans it up
    final var second = transactionContext.getCurrentTransaction();
    final var exportedStrings = state.getEncodedStrings();
    final var exportedMetrics = collect();
    state.cleanUp();
    increment("type3", "tenant3", "worker3", JobMetricsExportState.CREATED);
    second.commit();

    // then
    assertThat(exportedStrings)
        .containsExactly("type1", "tenant1", "worker1", "type2", "tenant2", "worker2");
    assertThat(exportedMetrics).hasSize(2);
    assertThat(jobMetricRows()).containsExactly(tuple("type3", "tenant3", "worker3", 1, 0, 0));
    assertThat(state.getEncodedStrings()).containsExactly("type3", "tenant3", "worker3");
    assertThat(state.isIncompleteBatch()).isFalse();
  }

  @Test
  void shouldCleanUpCommittedMetricsAndResetTheBatch() throws Exception {
    // given
    final var first = transactionContext.getCurrentTransaction();
    increment("type1", "tenant1", "worker1", JobMetricsExportState.CREATED);
    first.commit();

    // when
    final var second = transactionContext.getCurrentTransaction();
    state.cleanUp();
    second.commit();

    // then
    assertThat(jobMetricRows()).isEmpty();
    assertThat(collect()).isEmpty();
    assertThat(state.getBatchStartTime()).isEqualTo(-1L);
    assertThat(state.getBatchEndTime()).isEqualTo(-1L);
  }

  @Test
  void shouldEnforceTheUniqueKeysLimitAcrossTransactions() throws Exception {
    // given - a committed batch that has reached its limit
    createJobMetricsState(new EngineConfiguration().setJobMetricsMaxUniqueKeys(2));
    final var first = transactionContext.getCurrentTransaction();
    increment("type1", "tenant1", "worker1", JobMetricsExportState.CREATED);
    increment("type2", "tenant1", "worker1", JobMetricsExportState.CREATED);
    first.commit();

    // when
    final var second = transactionContext.getCurrentTransaction();
    increment("type3", "tenant1", "worker1", JobMetricsExportState.CREATED);
    increment("type2", "tenant1", "worker1", JobMetricsExportState.COMPLETED);
    second.commit();

    // then - the existing metric was incremented, the new one rejected and the batch marked
    assertThat(jobMetricRows())
        .containsExactly(
            tuple("type1", "tenant1", "worker1", 1, 0, 0),
            tuple("type2", "tenant1", "worker1", 1, 1, 0));
    assertThat(state.isIncompleteBatch()).isTrue();
  }

  private void increment(
      final String type,
      final String tenant,
      final String worker,
      final JobMetricsExportState status) {
    state.incrementMetric(
        new JobRecord().setType(type).setTenantId(tenant).setWorker(worker), status);
  }

  private List<StatusMetrics[]> collect() {
    final List<StatusMetrics[]> result = new ArrayList<>();
    state.forEach((jobTypeIdx, tenantIdx, workerIdx, metrics) -> result.add(metrics));
    return result;
  }

  private List<Tuple> jobMetricRows() throws SQLException {
    return rows(
        "SELECT job_type, tenant_id, worker, created_count, completed_count, failed_count"
            + " FROM CAM_RU_JOB_METRIC");
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
