/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.state;

import io.camunda.analytics.lake.state.TranslatorState.OpenElement;
import io.camunda.analytics.lake.state.TranslatorState.OpenInstance;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dumps the translator's open {@link TranslatorState} as queryable Parquet — {@code
 * open_instances.parquet} and {@code open_elements.parquet} — plus an {@code offsets.json}
 * consistency stamp, all written into one directory.
 *
 * <p><b>Consistency:</b> the dump is only consistent because its caller is the single translator
 * thread that also applies records to {@link TranslatorState}. {@code lastAppliedOffsets} must be
 * captured by the caller <em>before</em> {@link #dump} starts iterating the open state, and never
 * after — capturing it afterward could stamp an offset newer than what the iteration actually
 * observed, silently corrupting the consistency guarantee this file exists to provide. This class
 * does not itself enforce that ordering; it trusts the caller (see {@code LakePocApp}).
 *
 * <p><b>This file doubles as a checkpoint, not just a debugging view.</b> A future recovery path
 * could load {@code open_instances.parquet}/{@code open_elements.parquet} back into {@link
 * TranslatorState} and resume consumption from the stamped {@code offsets.json} instead of
 * replaying the source topic(s) from scratch. That possibility is why the offset stamp is not
 * optional: without it, this dump would only be an inspectable snapshot with no way to say which
 * records it does or doesn't reflect.
 *
 * <p><b>Also feeds the {@code open_instances_gauge} table.</b> {@link #dump} folds a per-process
 * open-instance tally into the same {@code forEachOpenInstance} scan it already runs, and returns
 * it — see {@code io.camunda.analytics.lake.write.OpenInstancesGaugeSampler}'s own javadoc for why
 * those gauge rows are wall-clock <em>observations</em>, not source-log-derived facts: they carry
 * no offsets, are never covered by this dump's own {@code offsets.json} consistency stamp, and are
 * not reproduced by any future replay/rebuild from the source log.
 */
public final class StateSnapshotDumper {

  private static final Logger LOG = LoggerFactory.getLogger(StateSnapshotDumper.class);

  private static final String OPEN_INSTANCES_TABLE = "dump_open_instances";
  private static final String OPEN_ELEMENTS_TABLE = "dump_open_elements";

  private static final String OPEN_INSTANCES_DDL =
      "CREATE OR REPLACE TEMP TABLE "
          + OPEN_INSTANCES_TABLE
          + " (key BIGINT, process_definition_key BIGINT, process_id VARCHAR, version INTEGER, "
          + "tenant_id VARCHAR, start_ms BIGINT)";

  private static final String OPEN_ELEMENTS_DDL =
      "CREATE OR REPLACE TEMP TABLE "
          + OPEN_ELEMENTS_TABLE
          + " (key BIGINT, instance_key BIGINT, process_id VARCHAR, version INTEGER, "
          + "tenant_id VARCHAR, element_id VARCHAR, element_type VARCHAR, start_ms BIGINT, "
          + "instance_start_ms BIGINT)";

  private final TranslatorState state;
  private final Path dumpDir;

  public StateSnapshotDumper(final TranslatorState state, final Path dumpDir) {
    this.state = state;
    this.dumpDir = dumpDir;
  }

  /**
   * Writes {@code open_instances.parquet}, {@code open_elements.parquet} and {@code offsets.json}
   * into the dump directory, replacing any prior dump there. See the class javadoc for the
   * consistency contract {@code lastAppliedOffsets} must satisfy.
   *
   * @return per-process open-instance counts, folded into the same {@code forEachOpenInstance}
   *     iteration this method already runs to populate {@code open_instances.parquet} — a zero-
   *     extra-scan byproduct feeding {@code
   *     io.camunda.analytics.lake.write.OpenInstancesGaugeSampler}'s periodic tick (see its own
   *     javadoc); a process with zero currently-open instances is simply absent from the map, not
   *     present with a zero count
   */
  public Map<String, Long> dump(final Map<Integer, Long> lastAppliedOffsets) {
    try {
      Files.createDirectories(dumpDir);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to create snapshot dump directory " + dumpDir, e);
    }

    final OpenInstanceCounts instanceCounts;
    final long elementCount;
    // One embedded DuckDB connection per dump -- a PoC simplification (see the caller's javadoc);
    // this is not on any hot path.
    try (Connection duckdb = DriverManager.getConnection("jdbc:duckdb:")) {
      instanceCounts = dumpOpenInstances(duckdb);
      elementCount = dumpOpenElements(duckdb);
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to dump translator state snapshot", e);
    }
    writeOffsets(lastAppliedOffsets);
    LOG.info(
        "Dumped state snapshot to {}: {} open instance(s), {} open element(s)",
        dumpDir,
        instanceCounts.total(),
        elementCount);
    return instanceCounts.byProcessId();
  }

  /**
   * Scans currently open instances and returns per-process open counts, without writing any dump
   * files — a standalone (unbatched with {@link #dump}) scan meant to be called once at startup, to
   * seed {@code io.camunda.analytics.lake.write.OpenInstancesGaugeSampler} with a t=0 sample before
   * the first periodic {@link #dump} tick fires. Unlike the per-process counts {@link #dump} folds
   * into its own existing iteration, this is an extra scan — an acceptable one-time startup cost,
   * not a recurring one.
   */
  public Map<String, Long> countOpenInstancesByProcess() {
    final Map<String, Long> counts = new HashMap<>();
    state.forEachOpenInstance((key, instance) -> counts.merge(instance.processId(), 1L, Long::sum));
    return counts;
  }

  private OpenInstanceCounts dumpOpenInstances(final Connection duckdb) throws SQLException {
    try (Statement ddl = duckdb.createStatement()) {
      ddl.execute(OPEN_INSTANCES_DDL);
    }
    final AtomicLong count = new AtomicLong();
    final Map<String, Long> byProcessId = new HashMap<>();
    final DuckDBConnection duckdbConnection = (DuckDBConnection) duckdb;
    try (DuckDBAppender appender = duckdbConnection.createAppender(OPEN_INSTANCES_TABLE)) {
      state.forEachOpenInstance(
          (key, instance) -> {
            appendOpenInstanceRow(appender, key, instance);
            count.incrementAndGet();
            byProcessId.merge(instance.processId(), 1L, Long::sum);
          });
    }
    copyToParquet(duckdb, OPEN_INSTANCES_TABLE, "open_instances");
    return new OpenInstanceCounts(count.get(), Map.copyOf(byProcessId));
  }

  /**
   * Total open-instance count plus the same tally broken down per process id (see {@link #dump}).
   */
  private record OpenInstanceCounts(long total, Map<String, Long> byProcessId) {}

  private long dumpOpenElements(final Connection duckdb) throws SQLException {
    try (Statement ddl = duckdb.createStatement()) {
      ddl.execute(OPEN_ELEMENTS_DDL);
    }
    final AtomicLong count = new AtomicLong();
    final DuckDBConnection duckdbConnection = (DuckDBConnection) duckdb;
    try (DuckDBAppender appender = duckdbConnection.createAppender(OPEN_ELEMENTS_TABLE)) {
      state.forEachOpenElement(
          (key, element) -> {
            appendOpenElementRow(appender, key, element);
            count.incrementAndGet();
          });
    }
    copyToParquet(duckdb, OPEN_ELEMENTS_TABLE, "open_elements");
    return count.get();
  }

  /** Mirrors {@link OpenInstance} field for field, plus the leading {@code key} column. */
  private static void appendOpenInstanceRow(
      final DuckDBAppender appender, final long key, final OpenInstance instance) {
    try {
      appender.beginRow();
      appender.append(key);
      appender.append(instance.processDefinitionKey());
      appender.append(instance.processId());
      appender.append(instance.version());
      appender.append(instance.tenantId());
      appender.append(instance.startMs());
      appender.endRow();
    } catch (final SQLException e) {
      // BiConsumer#accept cannot declare a checked exception -- rethrown unchecked, caught by
      // dump()'s SQLException handler only when it is the SQLException itself, so wrap plainly.
      throw new IllegalStateException("Failed to append open instance row " + key, e);
    }
  }

  /** Mirrors {@link OpenElement} field for field, plus the leading {@code key} column. */
  private static void appendOpenElementRow(
      final DuckDBAppender appender, final long key, final OpenElement element) {
    try {
      appender.beginRow();
      appender.append(key);
      appender.append(element.instanceKey());
      appender.append(element.processId());
      appender.append(element.version());
      appender.append(element.tenantId());
      appender.append(element.elementId());
      appender.append(element.elementType());
      appender.append(element.startMs());
      appender.append(element.instanceStartMs());
      appender.endRow();
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to append open element row " + key, e);
    }
  }

  /**
   * Copies {@code tableName} to {@code <outputName>.parquet} via a {@code .parquet.tmp} staging
   * file and an atomic rename, so a reader never observes a partially-written file.
   */
  private void copyToParquet(
      final Connection duckdb, final String tableName, final String outputName)
      throws SQLException {
    final Path tmpPath = dumpDir.resolve(outputName + ".parquet.tmp");
    final Path finalPath = dumpDir.resolve(outputName + ".parquet");
    try (Statement copy = duckdb.createStatement()) {
      copy.execute("COPY (SELECT * FROM " + tableName + ") TO '" + tmpPath + "' (FORMAT PARQUET)");
    }
    try {
      Files.move(tmpPath, finalPath, StandardCopyOption.ATOMIC_MOVE);
    } catch (final AtomicMoveNotSupportedException e) {
      try {
        Files.move(tmpPath, finalPath, StandardCopyOption.REPLACE_EXISTING);
      } catch (final IOException fallback) {
        throw new UncheckedIOException(
            "Failed to move snapshot file " + tmpPath + " to " + finalPath, fallback);
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(
          "Failed to move snapshot file " + tmpPath + " to " + finalPath, e);
    }
  }

  /** Hand-rolled JSON object of {@code partition -> lastAppliedOffset}; the dump's offset stamp. */
  private void writeOffsets(final Map<Integer, Long> offsets) {
    final StringBuilder json = new StringBuilder();
    json.append('{');
    boolean first = true;
    for (final Map.Entry<Integer, Long> entry : offsets.entrySet()) {
      if (!first) {
        json.append(',');
      }
      first = false;
      json.append('"').append(entry.getKey()).append("\":").append(entry.getValue());
    }
    json.append('}');
    final Path offsetsPath = dumpDir.resolve("offsets.json");
    try {
      Files.writeString(offsetsPath, json.toString());
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to write snapshot offsets to " + offsetsPath, e);
    }
  }
}
