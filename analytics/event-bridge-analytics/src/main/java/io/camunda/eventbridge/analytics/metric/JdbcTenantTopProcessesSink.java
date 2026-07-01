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
import org.apache.datasketches.common.ArrayOfStringsSerDe;
import org.apache.datasketches.frequencies.ItemsSketch;

/**
 * The serving sink for the top-processes (heavy-hitter) rollup: stores the <em>frequent-items
 * sketch</em> itself per (tenant, granularity, window), rather than a pre-ranked list. Because the
 * sketch is mergeable, the dashboard can union the windows in a selected range and derive the
 * ranking on read — the same "store a distribution, combine over the range, rank last" pattern the
 * duration percentiles use. Runs at several granularities in parallel (e.g. {@code 1m}, {@code
 * total}).
 */
public final class JdbcTenantTopProcessesSink
    implements ResultSink<Windowed<String>, ItemsSketch<String>> {

  private static final String CREATE =
      """
      CREATE TABLE IF NOT EXISTS top_processes_sketch (
        tenant_id       VARCHAR(255) NOT NULL,
        granularity     VARCHAR(16)  NOT NULL,
        window_start    BIGINT       NOT NULL,
        window_size_ms  BIGINT       NOT NULL,
        items_sketch    BLOB         NOT NULL,
        PRIMARY KEY (tenant_id, granularity, window_start)
      )""";

  private static final String MERGE =
      """
      MERGE INTO top_processes_sketch
        (tenant_id, granularity, window_start, window_size_ms, items_sketch)
        KEY (tenant_id, granularity, window_start)
        VALUES (?, ?, ?, ?, ?)""";

  private final DataSource dataSource;
  private final long windowSizeMs;
  private final String granularity;
  private final ArrayOfStringsSerDe serde = new ArrayOfStringsSerDe();

  public JdbcTenantTopProcessesSink(
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
      throw new IllegalStateException("Failed to initialize top-processes schema", e);
    }
  }

  @Override
  public void upsert(final Windowed<String> windowed, final ItemsSketch<String> sketch) {
    try (final Connection connection = dataSource.getConnection();
        final PreparedStatement merge = connection.prepareStatement(MERGE)) {
      merge.setString(1, windowed.key());
      merge.setString(2, granularity);
      merge.setLong(3, windowed.windowStart());
      merge.setLong(4, windowSizeMs);
      merge.setBytes(5, sketch.toByteArray(serde));
      merge.executeUpdate();
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to upsert top-processes sketch", e);
    }
  }
}
