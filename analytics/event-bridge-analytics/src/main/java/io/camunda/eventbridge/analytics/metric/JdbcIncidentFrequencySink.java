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
 * Serving sink for the incident-frequency metric: the number of incidents <em>raised</em> per flow
 * node per window (the {@code +1} create events, additive). A read sums it over a range to get
 * "incidents raised" and can group by flow node for the BPMN heatmap. Idempotent overwrite-by-key.
 */
public final class JdbcIncidentFrequencySink implements ResultSink<Windowed<IncidentKey>, Long> {

  private static final String CREATE =
      """
      CREATE TABLE IF NOT EXISTS incident_frequency_window (
        bpmn_process_id VARCHAR(255) NOT NULL,
        element_id      VARCHAR(255) NOT NULL,
        tenant_id       VARCHAR(255) NOT NULL,
        window_start    BIGINT       NOT NULL,
        window_size_ms  BIGINT       NOT NULL,
        incident_count  BIGINT       NOT NULL,
        PRIMARY KEY (bpmn_process_id, element_id, tenant_id, window_start)
      )""";

  private static final String MERGE =
      """
      MERGE INTO incident_frequency_window
        (bpmn_process_id, element_id, tenant_id, window_start, window_size_ms, incident_count)
        KEY (bpmn_process_id, element_id, tenant_id, window_start)
        VALUES (?, ?, ?, ?, ?, ?)""";

  private final DataSource dataSource;
  private final long windowSizeMs;

  public JdbcIncidentFrequencySink(final DataSource dataSource, final long windowSizeMs) {
    this.dataSource = dataSource;
    this.windowSizeMs = windowSizeMs;
  }

  public void initSchema() {
    try (final Connection connection = dataSource.getConnection();
        final Statement statement = connection.createStatement()) {
      statement.execute(CREATE);
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to initialize incident-frequency schema", e);
    }
  }

  @Override
  public void upsert(final Windowed<IncidentKey> windowed, final Long count) {
    final IncidentKey key = windowed.key();
    try (final Connection connection = dataSource.getConnection();
        final PreparedStatement merge = connection.prepareStatement(MERGE)) {
      merge.setString(1, key.bpmnProcessId());
      merge.setString(2, key.elementId());
      merge.setString(3, key.tenantId());
      merge.setLong(4, windowed.windowStart());
      merge.setLong(5, windowSizeMs);
      merge.setLong(6, count == null ? 0L : count);
      merge.executeUpdate();
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to upsert incident-frequency cell", e);
    }
  }
}
