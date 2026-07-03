/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.element;

import io.camunda.eventbridge.streaming.aggregate.ResultSink;
import io.camunda.eventbridge.streaming.window.Windowed;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.apache.datasketches.kll.KllDoublesSketch;
import org.apache.datasketches.quantilescommon.QuantileSearchCriteria;

/**
 * The serving sink for per-element (flow-node) duration <em>percentiles</em>. Runs at several time
 * granularities in parallel (e.g. {@code 1m} and {@code total}); each rollup writes its own {@code
 * granularity}-tagged rows, so the heatmap's all-time view reads a single exact {@code total} row
 * per element rather than merging windows. Complements {@link JdbcElementHeatmapSink}
 * (avg/min/max).
 */
public final class JdbcElementDurationPercentileSink
    implements ResultSink<Windowed<ElementKey>, KllDoublesSketch> {

  private static final String CREATE =
      """
      CREATE TABLE IF NOT EXISTS element_duration_pctl_window (
        bpmn_process_id        VARCHAR(255) NOT NULL,
        process_definition_key BIGINT       NOT NULL,
        version                INT          NOT NULL,
        tenant_id              VARCHAR(255) NOT NULL,
        element_id             VARCHAR(255) NOT NULL,
        element_type           VARCHAR(64)  NOT NULL,
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
        PRIMARY KEY (bpmn_process_id, process_definition_key, version, tenant_id, element_id,
                     granularity, window_start)
      )""";

  private static final String MERGE =
      """
      MERGE INTO element_duration_pctl_window
        (bpmn_process_id, process_definition_key, version, tenant_id, element_id, element_type,
         granularity, window_start, window_size_ms, observation_count, min_duration_ms,
         max_duration_ms, p50_duration_ms, p75_duration_ms, p90_duration_ms, p99_duration_ms,
         duration_sketch)
        KEY (bpmn_process_id, process_definition_key, version, tenant_id, element_id, granularity,
             window_start)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

  private final DataSource dataSource;
  private final long windowSizeMs;
  private final String granularity;

  public JdbcElementDurationPercentileSink(
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
      throw new IllegalStateException("Failed to initialize element duration-percentile schema", e);
    }
  }

  @Override
  public void upsert(final Windowed<ElementKey> windowed, final KllDoublesSketch sketch) {
    final ElementKey key = windowed.key();
    final boolean empty = sketch.isEmpty();
    try (final Connection connection = dataSource.getConnection();
        final PreparedStatement merge = connection.prepareStatement(MERGE)) {
      merge.setString(1, key.bpmnProcessId());
      merge.setLong(2, key.processDefinitionKey());
      merge.setInt(3, key.version());
      merge.setString(4, key.tenantId());
      merge.setString(5, key.elementId());
      merge.setString(6, key.elementType());
      merge.setString(7, granularity);
      merge.setLong(8, windowed.windowStart());
      merge.setLong(9, windowSizeMs);
      merge.setLong(10, sketch.getN());
      merge.setLong(11, empty ? 0L : Math.round(sketch.getMinItem()));
      merge.setLong(12, empty ? 0L : Math.round(sketch.getMaxItem()));
      merge.setLong(13, quantile(sketch, 0.5));
      merge.setLong(14, quantile(sketch, 0.75));
      merge.setLong(15, quantile(sketch, 0.9));
      merge.setLong(16, quantile(sketch, 0.99));
      merge.setBytes(17, sketch.toByteArray()); // mergeable state for exact range roll-ups on read
      merge.executeUpdate();
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to upsert element duration-percentile cell", e);
    }
  }

  private static long quantile(final KllDoublesSketch sketch, final double rank) {
    if (sketch.isEmpty()) {
      return 0L;
    }
    return Math.round(sketch.getQuantile(rank, QuantileSearchCriteria.INCLUSIVE));
  }
}
