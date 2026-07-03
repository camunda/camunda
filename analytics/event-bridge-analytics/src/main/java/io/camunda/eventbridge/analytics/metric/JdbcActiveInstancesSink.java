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
 * The serving sink for the <em>active instances</em> gauge: the running in-flight count per process
 * definition (sum of {@code +1} on activate / {@code -1} on complete over a single all-time
 * bucket). Idempotently overwrites the current value per definition; the dashboard reads it
 * directly and it does not depend on any time range.
 */
public final class JdbcActiveInstancesSink implements ResultSink<Windowed<DefinitionKey>, Long> {

  private static final String CREATE =
      """
      CREATE TABLE IF NOT EXISTS active_instances (
        bpmn_process_id        VARCHAR(255) NOT NULL,
        process_definition_key BIGINT       NOT NULL,
        version                INT          NOT NULL,
        tenant_id              VARCHAR(255) NOT NULL,
        active_count           BIGINT       NOT NULL,
        PRIMARY KEY (bpmn_process_id, process_definition_key, version, tenant_id)
      )""";

  private static final String MERGE =
      """
      MERGE INTO active_instances
        (bpmn_process_id, process_definition_key, version, tenant_id, active_count)
        KEY (bpmn_process_id, process_definition_key, version, tenant_id)
        VALUES (?, ?, ?, ?, ?)""";

  private final DataSource dataSource;

  public JdbcActiveInstancesSink(final DataSource dataSource) {
    this.dataSource = dataSource;
  }

  public void initSchema() {
    try (final Connection connection = dataSource.getConnection();
        final Statement statement = connection.createStatement()) {
      statement.execute(CREATE);
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to initialize active-instances schema", e);
    }
  }

  @Override
  public void upsert(final Windowed<DefinitionKey> windowed, final Long activeCount) {
    final DefinitionKey key = windowed.key();
    try (final Connection connection = dataSource.getConnection();
        final PreparedStatement merge = connection.prepareStatement(MERGE)) {
      merge.setString(1, key.bpmnProcessId());
      merge.setLong(2, key.processDefinitionKey());
      merge.setInt(3, key.version());
      merge.setString(4, key.tenantId());
      merge.setLong(5, Math.max(0L, activeCount)); // never show a transient negative
      merge.executeUpdate();
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to upsert active-instances gauge", e);
    }
  }
}
