/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.element;

import io.camunda.analytics.streaming.aggregate.RollupStore;
import io.camunda.analytics.streaming.window.Windowed;
import io.camunda.eventbridge.analytics.metric.ExecutionTimeAccumulator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;

/**
 * The heatmap serving store: merges buffered per-element partials into {@code
 * element_execution_window} as an upsert (the SQL form of the metric's {@code merge}). Each row
 * holds the executed count plus total/min/max duration for one element in one window; the webapp
 * derives the average and the heatmap colouring from it.
 */
public final class JdbcElementHeatmapStore
    implements RollupStore<Windowed<ElementKey>, ExecutionTimeAccumulator> {

  private static final String CREATE =
      """
      CREATE TABLE IF NOT EXISTS element_execution_window (
        bpmn_process_id        VARCHAR(255) NOT NULL,
        process_definition_key BIGINT       NOT NULL,
        version                INT          NOT NULL,
        tenant_id              VARCHAR(255) NOT NULL,
        element_id             VARCHAR(255) NOT NULL,
        element_type           VARCHAR(64)  NOT NULL,
        window_start           BIGINT       NOT NULL,
        window_size_ms         BIGINT       NOT NULL,
        executed_count         BIGINT       NOT NULL,
        total_duration_ms      BIGINT       NOT NULL,
        min_duration_ms        BIGINT       NOT NULL,
        max_duration_ms        BIGINT       NOT NULL,
        PRIMARY KEY (bpmn_process_id, process_definition_key, version, tenant_id, element_id,
                     window_start)
      )""";

  private static final String UPDATE =
      """
      UPDATE element_execution_window SET
        executed_count    = executed_count + ?,
        total_duration_ms = total_duration_ms + ?,
        min_duration_ms   = LEAST(min_duration_ms, ?),
        max_duration_ms   = GREATEST(max_duration_ms, ?)
      WHERE bpmn_process_id = ? AND process_definition_key = ? AND version = ? AND tenant_id = ?
        AND element_id = ? AND window_start = ?""";

  private static final String INSERT =
      """
      INSERT INTO element_execution_window
        (bpmn_process_id, process_definition_key, version, tenant_id, element_id, element_type,
         window_start, window_size_ms, executed_count, total_duration_ms, min_duration_ms,
         max_duration_ms)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

  private final DataSource dataSource;
  private final long windowSizeMs;

  public JdbcElementHeatmapStore(final DataSource dataSource, final long windowSizeMs) {
    this.dataSource = dataSource;
    this.windowSizeMs = windowSizeMs;
  }

  public void initSchema() {
    try (final Connection connection = dataSource.getConnection();
        final Statement statement = connection.createStatement()) {
      statement.execute(CREATE);
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to initialize element heatmap schema", e);
    }
  }

  @Override
  public void merge(final Map<Windowed<ElementKey>, ExecutionTimeAccumulator> partials) {
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
      throw new IllegalStateException("Failed to merge element heatmap partials", e);
    }
  }

  private void upsert(
      final Connection connection,
      final Windowed<ElementKey> windowed,
      final ExecutionTimeAccumulator acc)
      throws SQLException {
    final ElementKey key = windowed.key();
    try (final PreparedStatement update = connection.prepareStatement(UPDATE)) {
      update.setLong(1, acc.count());
      update.setLong(2, acc.totalMs());
      update.setLong(3, acc.minMs());
      update.setLong(4, acc.maxMs());
      update.setString(5, key.bpmnProcessId());
      update.setLong(6, key.processDefinitionKey());
      update.setInt(7, key.version());
      update.setString(8, key.tenantId());
      update.setString(9, key.elementId());
      update.setLong(10, windowed.windowStart());
      if (update.executeUpdate() > 0) {
        return;
      }
    }
    try (final PreparedStatement insert = connection.prepareStatement(INSERT)) {
      insert.setString(1, key.bpmnProcessId());
      insert.setLong(2, key.processDefinitionKey());
      insert.setInt(3, key.version());
      insert.setString(4, key.tenantId());
      insert.setString(5, key.elementId());
      insert.setString(6, key.elementType());
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
