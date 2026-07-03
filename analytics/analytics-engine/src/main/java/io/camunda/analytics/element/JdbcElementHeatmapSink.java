/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.element;

import io.camunda.analytics.metric.ExecutionTimeAccumulator;
import io.camunda.eventbridge.streaming.aggregate.ResultSink;
import io.camunda.eventbridge.streaming.window.Windowed;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

/**
 * The heatmap serving sink: idempotently writes the <em>full current accumulator</em> for one
 * window/element cell into {@code element_execution_window} via an overwrite-by-key {@code MERGE}.
 * Each row holds the executed count plus total/min/max duration for one element in one window; the
 * webapp derives the average and the heatmap colouring from it. The local aggregate is
 * authoritative, so writing the whole value makes re-emit converge — the RDBMS form of the generic
 * {@link ResultSink} contract.
 */
public final class JdbcElementHeatmapSink
    implements ResultSink<Windowed<ElementKey>, ExecutionTimeAccumulator> {

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

  private static final String MERGE =
      """
      MERGE INTO element_execution_window
        (bpmn_process_id, process_definition_key, version, tenant_id, element_id, element_type,
         window_start, window_size_ms, executed_count, total_duration_ms, min_duration_ms,
         max_duration_ms)
        KEY (bpmn_process_id, process_definition_key, version, tenant_id, element_id, window_start)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

  private final DataSource dataSource;
  private final long windowSizeMs;

  public JdbcElementHeatmapSink(final DataSource dataSource, final long windowSizeMs) {
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
  public void upsert(final Windowed<ElementKey> windowed, final ExecutionTimeAccumulator acc) {
    final ElementKey key = windowed.key();
    try (final Connection connection = dataSource.getConnection();
        final PreparedStatement merge = connection.prepareStatement(MERGE)) {
      merge.setString(1, key.bpmnProcessId());
      merge.setLong(2, key.processDefinitionKey());
      merge.setInt(3, key.version());
      merge.setString(4, key.tenantId());
      merge.setString(5, key.elementId());
      merge.setString(6, key.elementType());
      merge.setLong(7, windowed.windowStart());
      merge.setLong(8, windowSizeMs);
      merge.setLong(9, acc.count());
      merge.setLong(10, acc.totalMs());
      merge.setLong(11, acc.minMs());
      merge.setLong(12, acc.maxMs());
      merge.executeUpdate();
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to upsert element heatmap cell", e);
    }
  }
}
