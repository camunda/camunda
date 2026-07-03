/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.metric;

import io.camunda.eventbridge.streaming.aggregate.ResultSink;
import io.camunda.eventbridge.streaming.window.Windowed;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

/**
 * The serving sink for the forward-looking SLA-met metric, keyed by the instances' <em>start</em>
 * window (cohort). Idempotently writes the cohort's {@code started_count} (denominator) and {@code
 * met_count} (completed within target) into {@code sla_cohort_window}. The {@code sla_ms} target is
 * stored on the row so a read can tell whether a cohort has matured — a cohort is final once {@code
 * window_start + window_size_ms + sla_ms} has elapsed, before which {@code met_count} may still
 * rise (it is a lower bound). Re-emit converges since the row carries the whole value.
 */
public final class JdbcSlaCohortSink
    implements ResultSink<Windowed<DefinitionKey>, SlaCohortAccumulator> {

  private static final String CREATE =
      """
      CREATE TABLE IF NOT EXISTS sla_cohort_window (
        bpmn_process_id        VARCHAR(255) NOT NULL,
        process_definition_key BIGINT       NOT NULL,
        version                INT          NOT NULL,
        tenant_id              VARCHAR(255) NOT NULL,
        window_start           BIGINT       NOT NULL,
        window_size_ms         BIGINT       NOT NULL,
        sla_ms                 BIGINT       NOT NULL,
        started_count          BIGINT       NOT NULL,
        met_count              BIGINT       NOT NULL,
        settled_count          BIGINT       NOT NULL,
        PRIMARY KEY (bpmn_process_id, process_definition_key, version, tenant_id, window_start)
      )""";

  private static final String MERGE =
      """
      MERGE INTO sla_cohort_window
        (bpmn_process_id, process_definition_key, version, tenant_id, window_start, window_size_ms,
         sla_ms, started_count, met_count, settled_count)
        KEY (bpmn_process_id, process_definition_key, version, tenant_id, window_start)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

  private final DataSource dataSource;
  private final long windowSizeMs;
  private final long slaMs;

  public JdbcSlaCohortSink(final DataSource dataSource, final long windowSizeMs, final long slaMs) {
    this.dataSource = dataSource;
    this.windowSizeMs = windowSizeMs;
    this.slaMs = slaMs;
  }

  public void initSchema() {
    try (final Connection connection = dataSource.getConnection();
        final Statement statement = connection.createStatement()) {
      statement.execute(CREATE);
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to initialize SLA-cohort schema", e);
    }
  }

  @Override
  public void upsert(final Windowed<DefinitionKey> windowed, final SlaCohortAccumulator acc) {
    final DefinitionKey key = windowed.key();
    try (final Connection connection = dataSource.getConnection();
        final PreparedStatement merge = connection.prepareStatement(MERGE)) {
      merge.setString(1, key.bpmnProcessId());
      merge.setLong(2, key.processDefinitionKey());
      merge.setInt(3, key.version());
      merge.setString(4, key.tenantId());
      merge.setLong(5, windowed.windowStart());
      merge.setLong(6, windowSizeMs);
      merge.setLong(7, slaMs);
      merge.setLong(8, acc.started()); // denominator
      merge.setLong(9, acc.met()); // met within target (numerator)
      merge.setLong(10, acc.settled()); // outcomes seen so far (for the maturing band)
      merge.executeUpdate();
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to upsert SLA-cohort cell", e);
    }
  }
}
