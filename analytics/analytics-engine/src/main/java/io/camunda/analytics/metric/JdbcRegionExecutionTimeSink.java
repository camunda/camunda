/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.metric;

import io.camunda.eventbridge.streaming.aggregate.ResultSink;
import io.camunda.eventbridge.streaming.window.Windowed;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

/**
 * The serving sink for process-instance execution time grouped by region: idempotently writes the
 * <em>full current accumulator</em> for one window/region cell into {@code
 * proc_inst_exec_time_window} via an overwrite-by-key {@code MERGE}. Because the local aggregate is
 * authoritative and this writes the whole value (not a delta), re-emitting the same cell converges
 * instead of double-counting — the RDBMS form of the generic {@link ResultSink} contract.
 *
 * <p>Writes a fixed {@code dataset_id} so the existing webapp report (which scopes by dataset)
 * keeps working unchanged.
 */
public final class JdbcRegionExecutionTimeSink
    implements ResultSink<Windowed<RegionKey>, ExecutionTimeAccumulator> {

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

  private static final String MERGE =
      """
      MERGE INTO proc_inst_exec_time_window
        (dataset_id, region, process_definition_key, bpmn_process_id, version, tenant_id,
         window_start, window_size_ms, completed_count, total_duration_ms, min_duration_ms,
         max_duration_ms)
        KEY (dataset_id, region, process_definition_key, version, tenant_id, window_start)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

  private final DataSource dataSource;
  private final long datasetId;
  private final long windowSizeMs;

  public JdbcRegionExecutionTimeSink(
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
  public void upsert(final Windowed<RegionKey> windowed, final ExecutionTimeAccumulator acc) {
    final RegionKey key = windowed.key();
    try (final Connection connection = dataSource.getConnection();
        final PreparedStatement merge = connection.prepareStatement(MERGE)) {
      merge.setLong(1, datasetId);
      merge.setString(2, key.region());
      merge.setLong(3, key.processDefinitionKey());
      merge.setString(4, key.bpmnProcessId());
      merge.setInt(5, key.version());
      merge.setString(6, key.tenantId());
      merge.setLong(7, windowed.windowStart());
      merge.setLong(8, windowSizeMs);
      merge.setLong(9, acc.count());
      merge.setLong(10, acc.totalMs());
      merge.setLong(11, acc.minMs());
      merge.setLong(12, acc.maxMs());
      merge.executeUpdate();
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to upsert region execution-time cell", e);
    }
  }
}
