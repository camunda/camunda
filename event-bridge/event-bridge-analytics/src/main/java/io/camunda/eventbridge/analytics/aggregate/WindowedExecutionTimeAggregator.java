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
 * Phase-1 windowed aggregator (DB-as-merge, no shuffle, no coordinator): folds execution-time facts
 * into per-{@code (definition, version, tenant, window)} cells in an RDBMS, bucketing by completion
 * event-time. The window is part of the primary key, so each cell stays independent and many
 * instances can upsert concurrently — H2 row locking serializes the increments.
 *
 * <p>Correctness: each fact is deduped by its source coordinate via a per-source-partition
 * high-watermark advanced in the same transaction as the aggregate (effectively-exactly-once under
 * at-least-once delivery / replay). A single-row event-time watermark (max completion time seen)
 * drives window finalization — a window is final once {@code watermark - allowedLateness} has
 * passed its end.
 */
public final class WindowedExecutionTimeAggregator {

  /** Default window size: one hour. */
  public static final long DEFAULT_WINDOW_SIZE_MS = 3_600_000L;

  /** Default grace for out-of-order completions before a window is declared final. */
  public static final long DEFAULT_ALLOWED_LATENESS_MS = 60_000L;

  private static final int WATERMARK_ROW = 0;

  private static final String CREATE_WINDOW =
      """
      CREATE TABLE IF NOT EXISTS proc_inst_exec_time_window (
        process_definition_key BIGINT       NOT NULL,
        bpmn_process_id        VARCHAR(255) NOT NULL,
        version                INT          NOT NULL,
        tenant_id              VARCHAR(255) NOT NULL,
        window_start           BIGINT       NOT NULL,
        window_size_ms         BIGINT       NOT NULL,
        completed_count        BIGINT       NOT NULL,
        total_duration_ms      BIGINT       NOT NULL,
        min_duration_ms        BIGINT       NOT NULL,
        max_duration_ms        BIGINT       NOT NULL,
        PRIMARY KEY (process_definition_key, version, tenant_id, window_start)
      )""";

  private static final String CREATE_FACT_WATERMARK =
      """
      CREATE TABLE IF NOT EXISTS fact_consumer_watermark (
        source_partition_id INT    PRIMARY KEY,
        consumed_position   BIGINT NOT NULL
      )""";

  private static final String CREATE_EVENT_TIME_WATERMARK =
      """
      CREATE TABLE IF NOT EXISTS event_time_watermark (
        id             INT    PRIMARY KEY,
        max_event_time BIGINT NOT NULL
      )""";

  private static final String SELECT_FACT_WATERMARK =
      "SELECT consumed_position FROM fact_consumer_watermark WHERE source_partition_id = ?";
  private static final String UPSERT_FACT_WATERMARK =
      "MERGE INTO fact_consumer_watermark (source_partition_id, consumed_position) VALUES (?, ?)";

  private static final String SELECT_EVENT_TIME_WATERMARK =
      "SELECT max_event_time FROM event_time_watermark WHERE id = ?";
  private static final String UPSERT_EVENT_TIME_WATERMARK =
      "MERGE INTO event_time_watermark (id, max_event_time) VALUES (?, ?)";

  private static final String UPDATE_WINDOW =
      """
      UPDATE proc_inst_exec_time_window SET
        completed_count   = completed_count + 1,
        total_duration_ms = total_duration_ms + ?,
        min_duration_ms   = LEAST(min_duration_ms, ?),
        max_duration_ms   = GREATEST(max_duration_ms, ?)
      WHERE process_definition_key = ? AND version = ? AND tenant_id = ? AND window_start = ?""";

  private static final String INSERT_WINDOW =
      """
      INSERT INTO proc_inst_exec_time_window
        (process_definition_key, bpmn_process_id, version, tenant_id, window_start, window_size_ms,
         completed_count, total_duration_ms, min_duration_ms, max_duration_ms)
      VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, ?)""";

  private static final String SELECT_WINDOW =
      """
      SELECT bpmn_process_id, window_size_ms, completed_count, total_duration_ms,
             min_duration_ms, max_duration_ms
      FROM proc_inst_exec_time_window
      WHERE process_definition_key = ? AND version = ? AND tenant_id = ? AND window_start = ?""";

  private final DataSource dataSource;
  private final long windowSizeMs;
  private final long allowedLatenessMs;

  public WindowedExecutionTimeAggregator(final DataSource dataSource) {
    this(dataSource, DEFAULT_WINDOW_SIZE_MS, DEFAULT_ALLOWED_LATENESS_MS);
  }

  public WindowedExecutionTimeAggregator(
      final DataSource dataSource, final long windowSizeMs, final long allowedLatenessMs) {
    this.dataSource = dataSource;
    this.windowSizeMs = windowSizeMs;
    this.allowedLatenessMs = allowedLatenessMs;
  }

  public void initSchema() {
    try (final Connection connection = dataSource.getConnection();
        final Statement statement = connection.createStatement()) {
      statement.execute(CREATE_WINDOW);
      statement.execute(CREATE_FACT_WATERMARK);
      statement.execute(CREATE_EVENT_TIME_WATERMARK);
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to initialize windowed analytics schema", e);
    }
  }

  /** The event-time window start a completion timestamp falls into. */
  public long windowStartFor(final long eventTime) {
    return Math.floorDiv(eventTime, windowSizeMs) * windowSizeMs;
  }

  /**
   * Folds a fact into its window. Returns {@code true} if aggregated, {@code false} if it was a
   * duplicate already folded (by source coordinate).
   */
  public boolean apply(final ProcessInstanceExecutionTimeFact fact) {
    try (final Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try {
        if (fact.sourcePosition() <= readFactWatermark(connection, fact.sourcePartitionId())) {
          connection.rollback();
          return false;
        }
        upsertWindow(connection, fact, windowStartFor(fact.endTime()));
        advanceEventTimeWatermark(connection, fact.endTime());
        writeFactWatermark(connection, fact.sourcePartitionId(), fact.sourcePosition());
        connection.commit();
        return true;
      } catch (final SQLException e) {
        connection.rollback();
        throw e;
      }
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to aggregate windowed execution-time fact", e);
    }
  }

  public Optional<WindowedExecutionTime> read(
      final long processDefinitionKey,
      final int version,
      final String tenantId,
      final long windowStart) {
    final long watermark = eventTimeWatermark();
    try (final Connection connection = dataSource.getConnection();
        final PreparedStatement statement = connection.prepareStatement(SELECT_WINDOW)) {
      statement.setLong(1, processDefinitionKey);
      statement.setInt(2, version);
      statement.setString(3, tenantId);
      statement.setLong(4, windowStart);
      try (final ResultSet rs = statement.executeQuery()) {
        if (!rs.next()) {
          return Optional.empty();
        }
        final long windowSize = rs.getLong("window_size_ms");
        return Optional.of(
            new WindowedExecutionTime(
                processDefinitionKey,
                rs.getString("bpmn_process_id"),
                version,
                tenantId,
                windowStart,
                windowSize,
                rs.getLong("completed_count"),
                rs.getLong("total_duration_ms"),
                rs.getLong("min_duration_ms"),
                rs.getLong("max_duration_ms"),
                isFinal(watermark, windowStart + windowSize)));
      }
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to read windowed execution-time aggregate", e);
    }
  }

  /** The highest completion event-time observed, or {@link Long#MIN_VALUE} if none. */
  public long eventTimeWatermark() {
    try (final Connection connection = dataSource.getConnection()) {
      return readEventTimeWatermark(connection);
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to read event-time watermark", e);
    }
  }

  private boolean isFinal(final long watermark, final long windowEnd) {
    return watermark != Long.MIN_VALUE && watermark - allowedLatenessMs >= windowEnd;
  }

  private long readFactWatermark(final Connection connection, final int sourcePartitionId)
      throws SQLException {
    try (final PreparedStatement statement = connection.prepareStatement(SELECT_FACT_WATERMARK)) {
      statement.setInt(1, sourcePartitionId);
      try (final ResultSet rs = statement.executeQuery()) {
        return rs.next() ? rs.getLong(1) : -1L;
      }
    }
  }

  private void writeFactWatermark(
      final Connection connection, final int sourcePartitionId, final long position)
      throws SQLException {
    try (final PreparedStatement statement = connection.prepareStatement(UPSERT_FACT_WATERMARK)) {
      statement.setInt(1, sourcePartitionId);
      statement.setLong(2, position);
      statement.executeUpdate();
    }
  }

  private long readEventTimeWatermark(final Connection connection) throws SQLException {
    try (final PreparedStatement statement =
        connection.prepareStatement(SELECT_EVENT_TIME_WATERMARK)) {
      statement.setInt(1, WATERMARK_ROW);
      try (final ResultSet rs = statement.executeQuery()) {
        return rs.next() ? rs.getLong(1) : Long.MIN_VALUE;
      }
    }
  }

  private void advanceEventTimeWatermark(final Connection connection, final long eventTime)
      throws SQLException {
    final long current = readEventTimeWatermark(connection);
    final long next = Math.max(current, eventTime);
    try (final PreparedStatement statement =
        connection.prepareStatement(UPSERT_EVENT_TIME_WATERMARK)) {
      statement.setInt(1, WATERMARK_ROW);
      statement.setLong(2, next);
      statement.executeUpdate();
    }
  }

  private void upsertWindow(
      final Connection connection,
      final ProcessInstanceExecutionTimeFact fact,
      final long windowStart)
      throws SQLException {
    try (final PreparedStatement update = connection.prepareStatement(UPDATE_WINDOW)) {
      update.setLong(1, fact.durationMs());
      update.setLong(2, fact.durationMs());
      update.setLong(3, fact.durationMs());
      update.setLong(4, fact.processDefinitionKey());
      update.setInt(5, fact.version());
      update.setString(6, fact.tenantId());
      update.setLong(7, windowStart);
      if (update.executeUpdate() > 0) {
        return;
      }
    }
    try (final PreparedStatement insert = connection.prepareStatement(INSERT_WINDOW)) {
      insert.setLong(1, fact.processDefinitionKey());
      insert.setString(2, fact.bpmnProcessId());
      insert.setInt(3, fact.version());
      insert.setString(4, fact.tenantId());
      insert.setLong(5, windowStart);
      insert.setLong(6, windowSizeMs);
      insert.setLong(7, fact.durationMs());
      insert.setLong(8, fact.durationMs());
      insert.setLong(9, fact.durationMs());
      insert.executeUpdate();
    }
  }
}
