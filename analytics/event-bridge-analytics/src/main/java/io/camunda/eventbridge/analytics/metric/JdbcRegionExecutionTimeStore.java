/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.metric;

import io.camunda.analytics.streaming.aggregate.RollupStore;
import io.camunda.analytics.streaming.window.Windowed;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;

/**
 * The serving store for process-instance execution time grouped by region: merges buffered partials
 * into {@code proc_inst_exec_time_window} as an upsert (the SQL form of the metric's {@code
 * merge}).
 *
 * <p>Writes a fixed {@code dataset_id} so the existing webapp report (which scopes by dataset)
 * keeps working unchanged — the per-dataset multi-window concept is gone now that the metric is a
 * single library rollup with one window.
 */
public final class JdbcRegionExecutionTimeStore
    implements RollupStore<Windowed<RegionKey>, ExecutionTimeAccumulator> {

  private static final String CREATE =
      """
      CREATE TABLE IF NOT EXISTS proc_inst_exec_time_window (
        dataset_id             BIGINT       NOT NULL,
        region                 VARCHAR(255) NOT NULL,
        process_definition_key BIGINT       NOT NULL,
        bpmn_process_id        VARCHAR(255) NOT NULL,
        version                INT          NOT NULL,
        tenant_id              VARCHAR(255) NOT NULL,
        window_start           BIGINT       NOT NULL,
        window_size_ms         BIGINT       NOT NULL,
        completed_count        BIGINT       NOT NULL,
        total_duration_ms      BIGINT       NOT NULL,
        min_duration_ms        BIGINT       NOT NULL,
        max_duration_ms        BIGINT       NOT NULL,
        PRIMARY KEY (dataset_id, region, process_definition_key, version, tenant_id, window_start)
      )""";

  private static final String UPDATE =
      """
      UPDATE proc_inst_exec_time_window SET
        completed_count   = completed_count + ?,
        total_duration_ms = total_duration_ms + ?,
        min_duration_ms   = LEAST(min_duration_ms, ?),
        max_duration_ms   = GREATEST(max_duration_ms, ?)
      WHERE dataset_id = ? AND region = ? AND process_definition_key = ? AND version = ?
        AND tenant_id = ? AND window_start = ?""";

  private static final String INSERT =
      """
      INSERT INTO proc_inst_exec_time_window
        (dataset_id, region, process_definition_key, bpmn_process_id, version, tenant_id,
         window_start, window_size_ms, completed_count, total_duration_ms, min_duration_ms,
         max_duration_ms)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

  private final DataSource dataSource;
  private final long datasetId;
  private final long windowSizeMs;

  public JdbcRegionExecutionTimeStore(
      final DataSource dataSource, final long datasetId, final long windowSizeMs) {
    this.dataSource = dataSource;
    this.datasetId = datasetId;
    this.windowSizeMs = windowSizeMs;
  }

  public void initSchema() {
    try (final Connection connection = dataSource.getConnection();
        final Statement statement = connection.createStatement()) {
      statement.execute(CREATE);
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to initialize region execution-time schema", e);
    }
  }

  @Override
  public void merge(final Map<Windowed<RegionKey>, ExecutionTimeAccumulator> partials) {
    try (final Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try {
        for (final var entry : partials.entrySet()) {
          upsert(connection, entry.getKey(), entry.getValue());
        }
        connection.commit();
      } catch (final SQLException e) {
        connection.rollback();
        throw e;
      }
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to merge region execution-time partials", e);
    }
  }

  private void upsert(
      final Connection connection,
      final Windowed<RegionKey> windowed,
      final ExecutionTimeAccumulator acc)
      throws SQLException {
    final RegionKey key = windowed.key();
    try (final PreparedStatement update = connection.prepareStatement(UPDATE)) {
      update.setLong(1, acc.count());
      update.setLong(2, acc.totalMs());
      update.setLong(3, acc.minMs());
      update.setLong(4, acc.maxMs());
      update.setLong(5, datasetId);
      update.setString(6, key.region());
      update.setLong(7, key.processDefinitionKey());
      update.setInt(8, key.version());
      update.setString(9, key.tenantId());
      update.setLong(10, windowed.windowStart());
      if (update.executeUpdate() > 0) {
        return;
      }
    }
    try (final PreparedStatement insert = connection.prepareStatement(INSERT)) {
      insert.setLong(1, datasetId);
      insert.setString(2, key.region());
      insert.setLong(3, key.processDefinitionKey());
      insert.setString(4, key.bpmnProcessId());
      insert.setInt(5, key.version());
      insert.setString(6, key.tenantId());
      insert.setLong(7, windowed.windowStart());
      insert.setLong(8, windowSizeMs);
      insert.setLong(9, acc.count());
      insert.setLong(10, acc.totalMs());
      insert.setLong(11, acc.minMs());
      insert.setLong(12, acc.maxMs());
      insert.executeUpdate();
    }
  }
}
