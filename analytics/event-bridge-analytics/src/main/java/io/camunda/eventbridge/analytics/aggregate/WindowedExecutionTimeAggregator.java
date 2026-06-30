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
import java.util.Collection;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Windowed aggregator (DB-as-merge, no shuffle, no coordinator): folds each execution-time fact
 * into one cell <em>per declared dataset</em>, keyed by {@code (dataset, definition, version,
 * tenant, window)} and bucketed by completion event-time using that dataset's window. So creating a
 * dataset with a different window produces its own independent rollup (e.g. hourly vs daily), and
 * many datasets reuse the single fact stream.
 *
 * <p>Correctness: each fact is deduped by its source coordinate via a per-source-partition
 * high-watermark advanced in the same transaction as all the per-dataset upserts
 * (effectively-exactly-once under at-least-once delivery / replay). A single-row event-time
 * watermark (max completion time seen) drives window finalization. Datasets created later are
 * forward-only (they aggregate facts from their creation onward; backfill is a separate step).
 */
public final class WindowedExecutionTimeAggregator {

  /** Default window size: one hour. */
  public static final long DEFAULT_WINDOW_SIZE_MS = 3_600_000L;

  /** Default grace for out-of-order completions before a window is declared final. */
  public static final long DEFAULT_ALLOWED_LATENESS_MS = 60_000L;

  /** The variable used as the {@code region} grouping dimension. */
  public static final String REGION_VARIABLE = "region";

  /** Placeholder region for instances that have no {@code region} variable. */
  public static final String NO_REGION = "<none>";

  private static final int WATERMARK_ROW = 0;

  private static final String CREATE_WINDOW =
      """
      CREATE TABLE IF NOT EXISTS proc_inst_exec_time_window (
        dataset_id             BIGINT       NOT NULL,
        region                 VARCHAR(255) NOT NULL,
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
        PRIMARY KEY (dataset_id, region, process_definition_key, version, tenant_id, window_start)
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
      WHERE dataset_id = ? AND region = ? AND process_definition_key = ? AND version = ?
        AND tenant_id = ? AND window_start = ?""";

  private static final String INSERT_WINDOW =
      """
      INSERT INTO proc_inst_exec_time_window
        (dataset_id, region, process_definition_key, bpmn_process_id, version, tenant_id,
         window_start, window_size_ms, completed_count, total_duration_ms, min_duration_ms,
         max_duration_ms)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?)""";

  private static final String SELECT_WINDOW =
      """
      SELECT bpmn_process_id, window_size_ms, completed_count, total_duration_ms,
             min_duration_ms, max_duration_ms
      FROM proc_inst_exec_time_window
      WHERE dataset_id = ? AND region = ? AND process_definition_key = ? AND version = ?
        AND tenant_id = ? AND window_start = ?""";

  private final DataSource dataSource;
  private final long allowedLatenessMs;

  public WindowedExecutionTimeAggregator(final DataSource dataSource) {
    this(dataSource, DEFAULT_ALLOWED_LATENESS_MS);
  }

  public WindowedExecutionTimeAggregator(
      final DataSource dataSource, final long allowedLatenessMs) {
    this.dataSource = dataSource;
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

  /** The event-time window start a completion timestamp falls into for the given window size. */
  public long windowStartFor(final long eventTime, final long windowSizeMs) {
    return Math.floorDiv(eventTime, windowSizeMs) * windowSizeMs;
  }

  /**
   * Folds a fact into a cell of every given dataset (each with its own window), in one transaction.
   * Returns {@code true} if aggregated, {@code false} if it was a duplicate already folded (by
   * source coordinate) or there were no datasets to fold into.
   */
  public boolean apply(
      final ProcessInstanceExecutionTimeFact fact, final Collection<AggregateDataset> datasets) {
    if (datasets.isEmpty()) {
      return false;
    }
    try (final Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try {
        if (fact.sourcePosition() <= readFactWatermark(connection, fact.sourcePartitionId())) {
          connection.rollback();
          return false;
        }
        final String region = fact.variables().getOrDefault(REGION_VARIABLE, NO_REGION);
        for (final AggregateDataset dataset : datasets) {
          upsertWindow(
              connection,
              fact,
              dataset.id(),
              region,
              windowStartFor(fact.endTime(), dataset.windowSizeMs()),
              dataset.windowSizeMs());
        }
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
      final long datasetId,
      final String region,
      final long processDefinitionKey,
      final int version,
      final String tenantId,
      final long windowStart) {
    final long watermark = eventTimeWatermark();
    try (final Connection connection = dataSource.getConnection();
        final PreparedStatement statement = connection.prepareStatement(SELECT_WINDOW)) {
      statement.setLong(1, datasetId);
      statement.setString(2, region);
      statement.setLong(3, processDefinitionKey);
      statement.setInt(4, version);
      statement.setString(5, tenantId);
      statement.setLong(6, windowStart);
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
      final long datasetId,
      final String region,
      final long windowStart,
      final long windowSizeMs)
      throws SQLException {
    try (final PreparedStatement update = connection.prepareStatement(UPDATE_WINDOW)) {
      update.setLong(1, fact.durationMs());
      update.setLong(2, fact.durationMs());
      update.setLong(3, fact.durationMs());
      update.setLong(4, datasetId);
      update.setString(5, region);
      update.setLong(6, fact.processDefinitionKey());
      update.setInt(7, fact.version());
      update.setString(8, fact.tenantId());
      update.setLong(9, windowStart);
      if (update.executeUpdate() > 0) {
        return;
      }
    }
    try (final PreparedStatement insert = connection.prepareStatement(INSERT_WINDOW)) {
      insert.setLong(1, datasetId);
      insert.setString(2, region);
      insert.setLong(3, fact.processDefinitionKey());
      insert.setString(4, fact.bpmnProcessId());
      insert.setInt(5, fact.version());
      insert.setString(6, fact.tenantId());
      insert.setLong(7, windowStart);
      insert.setLong(8, windowSizeMs);
      insert.setLong(9, fact.durationMs());
      insert.setLong(10, fact.durationMs());
      insert.setLong(11, fact.durationMs());
      insert.executeUpdate();
    }
  }
}
