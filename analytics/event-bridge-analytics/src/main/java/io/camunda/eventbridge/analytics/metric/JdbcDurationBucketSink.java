/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.metric;

import io.camunda.eventbridge.streaming.aggregate.ResultSink;
import io.camunda.eventbridge.streaming.window.Windowed;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

/**
 * Serving sink for the completion-time distribution per start cohort: {@code started} plus the five
 * default duration bands (≤10s, ≤30s, ≤60s, ≤120s, &gt;120s). Idempotent overwrite-by-key; the read
 * derives "still running" as {@code started − Σ bands}.
 */
public final class JdbcDurationBucketSink
    implements ResultSink<Windowed<DefinitionKey>, DurationBucketAccumulator> {

  private static final String CREATE =
      """
      CREATE TABLE IF NOT EXISTS duration_bucket_window (
        bpmn_process_id        VARCHAR(255) NOT NULL,
        process_definition_key BIGINT       NOT NULL,
        version                INT          NOT NULL,
        tenant_id              VARCHAR(255) NOT NULL,
        window_start           BIGINT       NOT NULL,
        window_size_ms         BIGINT       NOT NULL,
        started_count          BIGINT       NOT NULL,
        le10s                  BIGINT       NOT NULL,
        le30s                  BIGINT       NOT NULL,
        le60s                  BIGINT       NOT NULL,
        le120s                 BIGINT       NOT NULL,
        gt120s                 BIGINT       NOT NULL,
        PRIMARY KEY (bpmn_process_id, process_definition_key, version, tenant_id, window_start)
      )""";

  private static final String MERGE =
      """
      MERGE INTO duration_bucket_window
        (bpmn_process_id, process_definition_key, version, tenant_id, window_start, window_size_ms,
         started_count, le10s, le30s, le60s, le120s, gt120s)
        KEY (bpmn_process_id, process_definition_key, version, tenant_id, window_start)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

  private final DataSource dataSource;
  private final long windowSizeMs;

  public JdbcDurationBucketSink(final DataSource dataSource, final long windowSizeMs) {
    this.dataSource = dataSource;
    this.windowSizeMs = windowSizeMs;
  }

  public void initSchema() {
    try (final Connection connection = dataSource.getConnection();
        final Statement statement = connection.createStatement()) {
      statement.execute(CREATE);
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to initialize duration-bucket schema", e);
    }
  }

  @Override
  public void upsert(final Windowed<DefinitionKey> windowed, final DurationBucketAccumulator acc) {
    final DefinitionKey key = windowed.key();
    final long[] b = acc.buckets();
    try (final Connection connection = dataSource.getConnection();
        final PreparedStatement merge = connection.prepareStatement(MERGE)) {
      merge.setString(1, key.bpmnProcessId());
      merge.setLong(2, key.processDefinitionKey());
      merge.setInt(3, key.version());
      merge.setString(4, key.tenantId());
      merge.setLong(5, windowed.windowStart());
      merge.setLong(6, windowSizeMs);
      merge.setLong(7, acc.started());
      merge.setLong(8, b.length > 0 ? b[0] : 0L);
      merge.setLong(9, b.length > 1 ? b[1] : 0L);
      merge.setLong(10, b.length > 2 ? b[2] : 0L);
      merge.setLong(11, b.length > 3 ? b[3] : 0L);
      merge.setLong(12, b.length > 4 ? b[4] : 0L);
      merge.executeUpdate();
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to upsert duration-bucket cell", e);
    }
  }
}
