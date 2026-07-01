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
import org.apache.datasketches.kll.KllDoublesSketch;
import org.apache.datasketches.quantilescommon.QuantileSearchCriteria;

/**
 * The serving sink for process-instance duration <em>percentiles</em> grouped by process
 * definition. The same metric runs at several time granularities in parallel (e.g. {@code 1m} for
 * the trend, {@code total} for the exact all-time figure); each rollup writes its own {@code
 * granularity}- tagged rows, so a read at a given granularity is a single-row lookup with exact
 * numbers — no cross-window merge. Because aggregating raw facts into a coarse window is identical
 * to merging the fine windows' sketches, the coarse rows are exact, not approximations.
 */
public final class JdbcDefinitionDurationPercentileSink
    implements ResultSink<Windowed<DefinitionKey>, KllDoublesSketch> {

  private static final String CREATE =
      """
      CREATE TABLE IF NOT EXISTS proc_inst_duration_pctl_window (
        bpmn_process_id        VARCHAR(255) NOT NULL,
        process_definition_key BIGINT       NOT NULL,
        version                INT          NOT NULL,
        tenant_id              VARCHAR(255) NOT NULL,
        granularity            VARCHAR(16)  NOT NULL,
        window_start           BIGINT       NOT NULL,
        window_size_ms         BIGINT       NOT NULL,
        observation_count      BIGINT       NOT NULL,
        min_duration_ms        BIGINT       NOT NULL,
        max_duration_ms        BIGINT       NOT NULL,
        p50_duration_ms        BIGINT       NOT NULL,
        p75_duration_ms        BIGINT       NOT NULL,
        p90_duration_ms        BIGINT       NOT NULL,
        p99_duration_ms        BIGINT       NOT NULL,
        duration_sketch        BLOB,
        PRIMARY KEY (bpmn_process_id, process_definition_key, version, tenant_id, granularity,
                     window_start)
      )""";

  private static final String MERGE =
      """
      MERGE INTO proc_inst_duration_pctl_window
        (bpmn_process_id, process_definition_key, version, tenant_id, granularity, window_start,
         window_size_ms, observation_count, min_duration_ms, max_duration_ms, p50_duration_ms,
         p75_duration_ms, p90_duration_ms, p99_duration_ms, duration_sketch)
        KEY (bpmn_process_id, process_definition_key, version, tenant_id, granularity, window_start)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

  private final DataSource dataSource;
  private final long windowSizeMs;
  private final String granularity;

  public JdbcDefinitionDurationPercentileSink(
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
      throw new IllegalStateException("Failed to initialize duration-percentile schema", e);
    }
  }

  @Override
  public void upsert(final Windowed<DefinitionKey> windowed, final KllDoublesSketch sketch) {
    final DefinitionKey key = windowed.key();
    final boolean empty = sketch.isEmpty();
    try (final Connection connection = dataSource.getConnection();
        final PreparedStatement merge = connection.prepareStatement(MERGE)) {
      merge.setString(1, key.bpmnProcessId());
      merge.setLong(2, key.processDefinitionKey());
      merge.setInt(3, key.version());
      merge.setString(4, key.tenantId());
      merge.setString(5, granularity);
      merge.setLong(6, windowed.windowStart());
      merge.setLong(7, windowSizeMs);
      merge.setLong(8, sketch.getN());
      merge.setLong(9, empty ? 0L : Math.round(sketch.getMinItem()));
      merge.setLong(10, empty ? 0L : Math.round(sketch.getMaxItem()));
      merge.setLong(11, quantile(sketch, 0.5));
      merge.setLong(12, quantile(sketch, 0.75));
      merge.setLong(13, quantile(sketch, 0.9));
      merge.setLong(14, quantile(sketch, 0.99));
      merge.setBytes(15, sketch.toByteArray()); // mergeable state for exact range roll-ups on read
      merge.executeUpdate();
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to upsert duration-percentile cell", e);
    }
  }

  private static long quantile(final KllDoublesSketch sketch, final double rank) {
    if (sketch.isEmpty()) {
      return 0L;
    }
    return Math.round(sketch.getQuantile(rank, QuantileSearchCriteria.INCLUSIVE));
  }
}
