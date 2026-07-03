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
 * Serving sink for the open-incidents gauge: {@code created − resolved} per flow node in a single
 * all-time bucket = the number of incidents currently open. Range-independent, like the
 * active-instances gauge; clamped at zero to absorb transient out-of-order resolves during replay.
 */
public final class JdbcOpenIncidentsSink implements ResultSink<Windowed<IncidentKey>, Long> {

  private static final String CREATE =
      """
      CREATE TABLE IF NOT EXISTS open_incidents (
        bpmn_process_id VARCHAR(255) NOT NULL,
        element_id      VARCHAR(255) NOT NULL,
        tenant_id       VARCHAR(255) NOT NULL,
        open_count      BIGINT       NOT NULL,
        PRIMARY KEY (bpmn_process_id, element_id, tenant_id)
      )""";

  private static final String MERGE =
      """
      MERGE INTO open_incidents (bpmn_process_id, element_id, tenant_id, open_count)
        KEY (bpmn_process_id, element_id, tenant_id)
        VALUES (?, ?, ?, ?)""";

  private final DataSource dataSource;

  public JdbcOpenIncidentsSink(final DataSource dataSource) {
    this.dataSource = dataSource;
  }

  public void initSchema() {
    try (final Connection connection = dataSource.getConnection();
        final Statement statement = connection.createStatement()) {
      statement.execute(CREATE);
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to initialize open-incidents schema", e);
    }
  }

  @Override
  public void upsert(final Windowed<IncidentKey> windowed, final Long delta) {
    final IncidentKey key = windowed.key();
    try (final Connection connection = dataSource.getConnection();
        final PreparedStatement merge = connection.prepareStatement(MERGE)) {
      merge.setString(1, key.bpmnProcessId());
      merge.setString(2, key.elementId());
      merge.setString(3, key.tenantId());
      merge.setLong(4, Math.max(0L, delta == null ? 0L : delta));
      merge.executeUpdate();
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to upsert open-incidents cell", e);
    }
  }
}
