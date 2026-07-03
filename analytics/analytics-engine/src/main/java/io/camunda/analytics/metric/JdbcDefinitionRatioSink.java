/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.metric;

import io.camunda.eventbridge.streaming.aggregate.RatioAccumulator;
import io.camunda.eventbridge.streaming.aggregate.ResultSink;
import io.camunda.eventbridge.streaming.window.Windowed;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

/**
 * The serving sink for a per-definition percentage/ratio metric: idempotently writes the full
 * current matched/total counts and the derived fraction for one window/definition cell into {@code
 * proc_ratio_window} via an overwrite-by-key {@code MERGE}. A {@code metric} label column keys the
 * measure (e.g. {@code sla_met}, {@code no_incident}) so several ratio rollups share one table.
 * Because the accumulator is authoritative and the row carries the whole value, re-emit converges —
 * the RDBMS form of the idempotent {@link ResultSink} contract. Serves Optimize's default {@code
 * percentSLAMet} / {@code percentNoIncidents} tiles.
 */
public final class JdbcDefinitionRatioSink
    implements ResultSink<Windowed<DefinitionKey>, RatioAccumulator> {

  private static final String CREATE =
      """
      CREATE TABLE IF NOT EXISTS proc_ratio_window (
        bpmn_process_id        VARCHAR(255) NOT NULL,
        process_definition_key BIGINT       NOT NULL,
        version                INT          NOT NULL,
        tenant_id              VARCHAR(255) NOT NULL,
        window_start           BIGINT       NOT NULL,
        window_size_ms         BIGINT       NOT NULL,
        metric                 VARCHAR(64)  NOT NULL,
        matched_count          BIGINT       NOT NULL,
        total_count            BIGINT       NOT NULL,
        ratio                  DOUBLE       NOT NULL,
        PRIMARY KEY (bpmn_process_id, process_definition_key, version, tenant_id, window_start,
                     metric)
      )""";

  private static final String MERGE =
      """
      MERGE INTO proc_ratio_window
        (bpmn_process_id, process_definition_key, version, tenant_id, window_start, window_size_ms,
         metric, matched_count, total_count, ratio)
        KEY (bpmn_process_id, process_definition_key, version, tenant_id, window_start, metric)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

  private final DataSource dataSource;
  private final long windowSizeMs;
  private final String metric;

  public JdbcDefinitionRatioSink(
      final DataSource dataSource, final long windowSizeMs, final String metric) {
    this.dataSource = dataSource;
    this.windowSizeMs = windowSizeMs;
    this.metric = metric;
  }

  public void initSchema() {
    try (final Connection connection = dataSource.getConnection();
        final Statement statement = connection.createStatement()) {
      statement.execute(CREATE);
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to initialize ratio schema", e);
    }
  }

  @Override
  public void upsert(final Windowed<DefinitionKey> windowed, final RatioAccumulator acc) {
    final DefinitionKey key = windowed.key();
    final double ratio = acc.total() == 0L ? 0.0 : (double) acc.matched() / acc.total();
    try (final Connection connection = dataSource.getConnection();
        final PreparedStatement merge = connection.prepareStatement(MERGE)) {
      merge.setString(1, key.bpmnProcessId());
      merge.setLong(2, key.processDefinitionKey());
      merge.setInt(3, key.version());
      merge.setString(4, key.tenantId());
      merge.setLong(5, windowed.windowStart());
      merge.setLong(6, windowSizeMs);
      merge.setString(7, metric);
      merge.setLong(8, acc.matched());
      merge.setLong(9, acc.total());
      merge.setDouble(10, ratio);
      merge.executeUpdate();
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to upsert ratio cell", e);
    }
  }
}
