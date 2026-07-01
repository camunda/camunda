/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.metric;

import io.camunda.analytics.streaming.aggregate.ResultSink;
import io.camunda.analytics.streaming.window.Windowed;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

/**
 * Serving sink for incident resolution time per flow node: count + total (+ max) of open→resolve
 * durations per window, from which a read derives the average for the incident-duration heatmap.
 * Additive, idempotent overwrite-by-key.
 */
public final class JdbcIncidentDurationSink
    implements ResultSink<Windowed<IncidentKey>, ExecutionTimeAccumulator> {

  private static final String CREATE =
      """
      CREATE TABLE IF NOT EXISTS incident_duration_window (
        bpmn_process_id   VARCHAR(255) NOT NULL,
        element_id        VARCHAR(255) NOT NULL,
        tenant_id         VARCHAR(255) NOT NULL,
        window_start      BIGINT       NOT NULL,
        window_size_ms    BIGINT       NOT NULL,
        incident_count    BIGINT       NOT NULL,
        total_duration_ms BIGINT       NOT NULL,
        max_duration_ms   BIGINT       NOT NULL,
        PRIMARY KEY (bpmn_process_id, element_id, tenant_id, window_start)
      )""";

  private static final String MERGE =
      """
      MERGE INTO incident_duration_window
        (bpmn_process_id, element_id, tenant_id, window_start, window_size_ms,
         incident_count, total_duration_ms, max_duration_ms)
        KEY (bpmn_process_id, element_id, tenant_id, window_start)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)""";

  private final DataSource dataSource;
  private final long windowSizeMs;

  public JdbcIncidentDurationSink(final DataSource dataSource, final long windowSizeMs) {
    this.dataSource = dataSource;
    this.windowSizeMs = windowSizeMs;
  }

  public void initSchema() {
    try (final Connection connection = dataSource.getConnection();
        final Statement statement = connection.createStatement()) {
      statement.execute(CREATE);
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to initialize incident-duration schema", e);
    }
  }

  @Override
  public void upsert(final Windowed<IncidentKey> windowed, final ExecutionTimeAccumulator acc) {
    final IncidentKey key = windowed.key();
    try (final Connection connection = dataSource.getConnection();
        final PreparedStatement merge = connection.prepareStatement(MERGE)) {
      merge.setString(1, key.bpmnProcessId());
      merge.setString(2, key.elementId());
      merge.setString(3, key.tenantId());
      merge.setLong(4, windowed.windowStart());
      merge.setLong(5, windowSizeMs);
      merge.setLong(6, acc.count());
      merge.setLong(7, acc.totalMs());
      merge.setLong(8, acc.count() == 0 ? 0L : acc.maxMs());
      merge.executeUpdate();
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to upsert incident-duration cell", e);
    }
  }
}
