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
import org.apache.datasketches.hll.HllSketch;

/**
 * The serving sink for the distinct number of processes active per tenant/window: reads the full
 * current HLL sketch for one window/tenant cell and idempotently writes the estimated distinct
 * count with its confidence bounds into {@code proc_distinct_window} via an overwrite-by-key {@code
 * MERGE}. The sketch is authoritative and the row carries the whole derived value, so re-emit
 * converges — the RDBMS form of the idempotent {@link ResultSink} contract.
 */
public final class JdbcTenantDistinctProcessSink
    implements ResultSink<Windowed<String>, HllSketch> {

  /** Standard deviations for the stored confidence bounds (2 ≈ 95%), matching the metric. */
  private static final int NUM_STD_DEV = 2;

  private static final String CREATE =
      """
      CREATE TABLE IF NOT EXISTS proc_distinct_window (
        tenant_id         VARCHAR(255) NOT NULL,
        granularity       VARCHAR(16)  NOT NULL,
        window_start      BIGINT       NOT NULL,
        window_size_ms    BIGINT       NOT NULL,
        distinct_estimate BIGINT       NOT NULL,
        distinct_lower    BIGINT       NOT NULL,
        distinct_upper    BIGINT       NOT NULL,
        PRIMARY KEY (tenant_id, granularity, window_start)
      )""";

  private static final String MERGE =
      """
      MERGE INTO proc_distinct_window
        (tenant_id, granularity, window_start, window_size_ms,
         distinct_estimate, distinct_lower, distinct_upper)
        KEY (tenant_id, granularity, window_start)
        VALUES (?, ?, ?, ?, ?, ?, ?)""";

  private final DataSource dataSource;
  private final long windowSizeMs;
  private final String granularity;

  public JdbcTenantDistinctProcessSink(
      final DataSource dataSource, final long windowSizeMs, final String granularity) {
    this.dataSource = dataSource;
    this.windowSizeMs = windowSizeMs;
    this.granularity = granularity;
  }

  public void initSchema() {
    try (final Connection connection = dataSource.getConnection();
        final Statement statement = connection.createStatement()) {
      statement.execute(CREATE);
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to initialize distinct-process schema", e);
    }
  }

  @Override
  public void upsert(final Windowed<String> windowed, final HllSketch sketch) {
    try (final Connection connection = dataSource.getConnection();
        final PreparedStatement merge = connection.prepareStatement(MERGE)) {
      merge.setString(1, windowed.key());
      merge.setString(2, granularity);
      merge.setLong(3, windowed.windowStart());
      merge.setLong(4, windowSizeMs);
      merge.setLong(5, Math.round(sketch.getEstimate()));
      merge.setLong(6, Math.round(sketch.getLowerBound(NUM_STD_DEV)));
      merge.setLong(7, Math.round(sketch.getUpperBound(NUM_STD_DEV)));
      merge.executeUpdate();
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to upsert distinct-process cell", e);
    }
  }
}
