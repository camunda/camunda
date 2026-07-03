/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.metric;

import io.camunda.analytics.fact.ProcessDefinitionFact;
import io.camunda.eventbridge.streaming.aggregate.Rollup;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;

/**
 * A non-windowed sink for process-definition facts: upserts each definition's BPMN XML into {@code
 * process_definition} (keyed by {@code process_definition_key}) so the dashboard can render the
 * model behind the flow-node heatmap. Implemented as a {@link Rollup} — the fold fans {@code
 * PROCESS} facts to it like any other — but it carries no windowed state; {@code accept} writes the
 * whole value by key, so redelivery converges. A small in-memory seen-set skips redundant writes
 * for definitions already persisted this run.
 */
public final class JdbcProcessDefinitionSink implements Rollup<ProcessDefinitionFact> {

  private static final String CREATE =
      """
      CREATE TABLE IF NOT EXISTS process_definition (
        process_definition_key BIGINT       NOT NULL,
        bpmn_process_id        VARCHAR(255) NOT NULL,
        version                INT          NOT NULL,
        tenant_id              VARCHAR(255) NOT NULL,
        bpmn_xml               CLOB         NOT NULL,
        PRIMARY KEY (process_definition_key)
      )""";

  private static final String MERGE =
      """
      MERGE INTO process_definition
        (process_definition_key, bpmn_process_id, version, tenant_id, bpmn_xml)
        KEY (process_definition_key)
        VALUES (?, ?, ?, ?, ?)""";

  private final DataSource dataSource;
  private final Set<Long> seen = new HashSet<>();

  public JdbcProcessDefinitionSink(final DataSource dataSource) {
    this.dataSource = dataSource;
  }

  public void initSchema() {
    try (final Connection connection = dataSource.getConnection();
        final Statement statement = connection.createStatement()) {
      statement.execute(CREATE);
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to initialize process-definition schema", e);
    }
  }

  @Override
  public void accept(final ProcessDefinitionFact fact) {
    if (!seen.add(fact.processDefinitionKey())) {
      return; // already persisted this run
    }
    try (final Connection connection = dataSource.getConnection();
        final PreparedStatement merge = connection.prepareStatement(MERGE)) {
      merge.setLong(1, fact.processDefinitionKey());
      merge.setString(2, fact.bpmnProcessId());
      merge.setInt(3, fact.version());
      merge.setString(4, fact.tenantId());
      merge.setString(5, fact.bpmnXml());
      merge.executeUpdate();
    } catch (final SQLException e) {
      seen.remove(fact.processDefinitionKey()); // allow a retry on the next delivery
      throw new IllegalStateException("Failed to upsert process definition", e);
    }
  }

  @Override
  public void flush() {
    // writes are applied immediately in accept; nothing buffered
  }

  @Override
  public void close() {
    // the DataSource is shared and owned by the pipeline
  }
}
