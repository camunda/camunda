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
 * The serving sink for <em>activated instances</em>: how many instances of a definition started in
 * each window (a windowed count of activations). Additive, so a read simply sums the counts over
 * the selected range — the total number started in that range.
 */
public final class JdbcActivatedInstancesSink implements ResultSink<Windowed<DefinitionKey>, Long> {

  private static final String CREATE =
      """
      CREATE TABLE IF NOT EXISTS activated_instances_window (
        bpmn_process_id        VARCHAR(255) NOT NULL,
        process_definition_key BIGINT       NOT NULL,
        version                INT          NOT NULL,
        tenant_id              VARCHAR(255) NOT NULL,
        window_start           BIGINT       NOT NULL,
        window_size_ms         BIGINT       NOT NULL,
        activated_count        BIGINT       NOT NULL,
        PRIMARY KEY (bpmn_process_id, process_definition_key, version, tenant_id, window_start)
      )""";

  private static final String MERGE =
      """
      MERGE INTO activated_instances_window
        (bpmn_process_id, process_definition_key, version, tenant_id, window_start, window_size_ms,
         activated_count)
        KEY (bpmn_process_id, process_definition_key, version, tenant_id, window_start)
        VALUES (?, ?, ?, ?, ?, ?, ?)""";

  private final DataSource dataSource;
  private final long windowSizeMs;

  public JdbcActivatedInstancesSink(final DataSource dataSource, final long windowSizeMs) {
    this.dataSource = dataSource;
    this.windowSizeMs = windowSizeMs;
  }

  public void initSchema() {
    try (final Connection connection = dataSource.getConnection();
        final Statement statement = connection.createStatement()) {
      statement.execute(CREATE);
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to initialize activated-instances schema", e);
    }
  }

  @Override
  public void upsert(final Windowed<DefinitionKey> windowed, final Long activatedCount) {
    final DefinitionKey key = windowed.key();
    try (final Connection connection = dataSource.getConnection();
        final PreparedStatement merge = connection.prepareStatement(MERGE)) {
      merge.setString(1, key.bpmnProcessId());
      merge.setLong(2, key.processDefinitionKey());
      merge.setInt(3, key.version());
      merge.setString(4, key.tenantId());
      merge.setLong(5, windowed.windowStart());
      merge.setLong(6, windowSizeMs);
      merge.setLong(7, activatedCount);
      merge.executeUpdate();
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to upsert activated-instances cell", e);
    }
  }
}
