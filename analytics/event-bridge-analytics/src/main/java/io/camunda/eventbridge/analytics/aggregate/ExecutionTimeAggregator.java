/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.aggregate;

import io.camunda.eventbridge.analytics.fact.ProcessInstanceExecutionTimeFact;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Stage 3 — folds the fact stream into the aggregated dataset in an RDBMS (H2 for now).
 *
 * <p>Fact delivery is at-least-once, so each fact is deduped by the source coordinate of the
 * completion record that derived it: a per-source-partition high-watermark is updated in the
 * <em>same transaction</em> as the aggregate, and any fact at or below the watermark is dropped.
 * This makes aggregation effectively-exactly-once despite duplicate delivery (e.g. across a Stage-1
 * failover).
 */
public final class ExecutionTimeAggregator {

  private static final String CREATE_AGGREGATE =
      """
      CREATE TABLE IF NOT EXISTS proc_inst_exec_time_agg (
        process_definition_key BIGINT  NOT NULL,
        bpmn_process_id        VARCHAR(255) NOT NULL,
        version                INT     NOT NULL,
        tenant_id              VARCHAR(255) NOT NULL,
        instance_count         BIGINT  NOT NULL,
        total_duration_ms      BIGINT  NOT NULL,
        min_duration_ms        BIGINT  NOT NULL,
        max_duration_ms        BIGINT  NOT NULL,
        PRIMARY KEY (process_definition_key, version, tenant_id)
      )""";

  private static final String CREATE_WATERMARK =
      """
      CREATE TABLE IF NOT EXISTS fact_consumer_watermark (
        source_partition_id INT    PRIMARY KEY,
        consumed_position   BIGINT NOT NULL
      )""";

  private static final String SELECT_WATERMARK =
      "SELECT consumed_position FROM fact_consumer_watermark WHERE source_partition_id = ?";

  private static final String UPSERT_WATERMARK =
      "MERGE INTO fact_consumer_watermark (source_partition_id, consumed_position) VALUES (?, ?)";

  private static final String UPDATE_AGGREGATE =
      """
      UPDATE proc_inst_exec_time_agg SET
        instance_count    = instance_count + 1,
        total_duration_ms = total_duration_ms + ?,
        min_duration_ms   = LEAST(min_duration_ms, ?),
        max_duration_ms   = GREATEST(max_duration_ms, ?)
      WHERE process_definition_key = ? AND version = ? AND tenant_id = ?""";

  private static final String INSERT_AGGREGATE =
      """
      INSERT INTO proc_inst_exec_time_agg
        (process_definition_key, bpmn_process_id, version, tenant_id,
         instance_count, total_duration_ms, min_duration_ms, max_duration_ms)
      VALUES (?, ?, ?, ?, 1, ?, ?, ?)""";

  private static final String SELECT_AGGREGATE =
      """
      SELECT bpmn_process_id, instance_count, total_duration_ms, min_duration_ms, max_duration_ms
      FROM proc_inst_exec_time_agg
      WHERE process_definition_key = ? AND version = ? AND tenant_id = ?""";

  private final DataSource dataSource;

  public ExecutionTimeAggregator(final DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /** Creates the aggregate and watermark tables if they do not already exist. */
  public void initSchema() {
    try (final Connection connection = dataSource.getConnection();
        final Statement statement = connection.createStatement()) {
      statement.execute(CREATE_AGGREGATE);
      statement.execute(CREATE_WATERMARK);
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to initialize analytics aggregate schema", e);
    }
  }

  /**
   * Applies a fact to the aggregate, deduping by source coordinate. Returns {@code true} if the
   * fact was aggregated, {@code false} if it was a duplicate that had already been folded.
   */
  public boolean apply(final ProcessInstanceExecutionTimeFact fact) {
    try (final Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try {
        if (fact.sourcePosition() <= readWatermark(connection, fact.sourcePartitionId())) {
          connection.rollback();
          return false;
        }
        upsertAggregate(connection, fact);
        writeWatermark(connection, fact.sourcePartitionId(), fact.sourcePosition());
        connection.commit();
        return true;
      } catch (final SQLException e) {
        connection.rollback();
        throw e;
      }
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to aggregate execution-time fact", e);
    }
  }

  public Optional<ExecutionTimeAggregate> read(
      final long processDefinitionKey, final int version, final String tenantId) {
    try (final Connection connection = dataSource.getConnection();
        final PreparedStatement statement = connection.prepareStatement(SELECT_AGGREGATE)) {
      statement.setLong(1, processDefinitionKey);
      statement.setInt(2, version);
      statement.setString(3, tenantId);
      try (final ResultSet rs = statement.executeQuery()) {
        if (!rs.next()) {
          return Optional.empty();
        }
        return Optional.of(
            new ExecutionTimeAggregate(
                processDefinitionKey,
                rs.getString("bpmn_process_id"),
                version,
                tenantId,
                rs.getLong("instance_count"),
                rs.getLong("total_duration_ms"),
                rs.getLong("min_duration_ms"),
                rs.getLong("max_duration_ms")));
      }
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to read execution-time aggregate", e);
    }
  }

  private long readWatermark(final Connection connection, final int sourcePartitionId)
      throws SQLException {
    try (final PreparedStatement statement = connection.prepareStatement(SELECT_WATERMARK)) {
      statement.setInt(1, sourcePartitionId);
      try (final ResultSet rs = statement.executeQuery()) {
        return rs.next() ? rs.getLong(1) : -1L;
      }
    }
  }

  private void writeWatermark(
      final Connection connection, final int sourcePartitionId, final long position)
      throws SQLException {
    try (final PreparedStatement statement = connection.prepareStatement(UPSERT_WATERMARK)) {
      statement.setInt(1, sourcePartitionId);
      statement.setLong(2, position);
      statement.executeUpdate();
    }
  }

  private void upsertAggregate(
      final Connection connection, final ProcessInstanceExecutionTimeFact fact)
      throws SQLException {
    try (final PreparedStatement update = connection.prepareStatement(UPDATE_AGGREGATE)) {
      update.setLong(1, fact.durationMs());
      update.setLong(2, fact.durationMs());
      update.setLong(3, fact.durationMs());
      update.setLong(4, fact.processDefinitionKey());
      update.setInt(5, fact.version());
      update.setString(6, fact.tenantId());
      if (update.executeUpdate() > 0) {
        return;
      }
    }
    try (final PreparedStatement insert = connection.prepareStatement(INSERT_AGGREGATE)) {
      insert.setLong(1, fact.processDefinitionKey());
      insert.setString(2, fact.bpmnProcessId());
      insert.setInt(3, fact.version());
      insert.setString(4, fact.tenantId());
      insert.setLong(5, fact.durationMs());
      insert.setLong(6, fact.durationMs());
      insert.setLong(7, fact.durationMs());
      insert.executeUpdate();
    }
  }
}
