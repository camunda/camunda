/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.write;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.iceberg.AppendFiles;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DataFiles;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.Table;
import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link GaugeBatchSink} that hand-rolls a Parquet file (DuckDB {@code COPY}) plus an Iceberg
 * {@link Table#newAppend()} commit for a batch of buffered {@link GaugeSample}s — the same
 * DuckDB-writes-Parquet/iceberg-core-commits-it idiom {@link IcebergLakeWriter}'s own legacy
 * buffered path and {@link LakeCompactor} use, reusing {@link IcebergLakeWriter}'s embedded DuckDB
 * {@link Connection} and its own {@code open_instances_gauge} {@link Table} handle (see {@link
 * IcebergLakeWriter#openInstancesGaugeTable()}'s own javadoc for why no commit lock is needed).
 *
 * <p>This does <b>not</b> go through the L0 sink's segment-ring/descriptor-sink machinery: that
 * machinery is offset-tied (every commit stamps a {@code lake.offset.p*}/{@code
 * lake.frontier.p*}/{@code lake.zbpos.z*} property), and gauge rows carry none of those — see
 * {@code OpenInstancesGaugeSampler}'s class javadoc for why. Every {@link #writeBatch} commit here
 * is a plain {@link Table#newAppend()} with no summary properties set at all.
 */
public final class OpenInstancesGaugeWriter implements GaugeBatchSink {

  private static final String STAGING_TABLE = "staging_open_instances_gauge";

  private static final String STAGING_DDL =
      "CREATE OR REPLACE TEMP TABLE "
          + STAGING_TABLE
          + " (sampled_at_ms BIGINT, process_id VARCHAR, open_instances BIGINT)";

  /** Same millis-to-timestamptz idiom {@code IcebergLakeWriter}'s own staging SELECTs use. */
  private static final String SELECT_SQL =
      "SELECT to_timestamp(sampled_at_ms / 1000.0) AS sampled_at, process_id, open_instances FROM "
          + STAGING_TABLE;

  private static final long MILLIS_PER_DAY = 86_400_000L;

  private static final Logger LOG = LoggerFactory.getLogger(OpenInstancesGaugeWriter.class);

  private final Connection duckdb;
  private final Table table;

  public OpenInstancesGaugeWriter(final IcebergLakeWriter writer) {
    duckdb = writer.duckdbConnection();
    table = writer.openInstancesGaugeTable();
  }

  /**
   * Groups {@code batch} by family day (the table is {@code days(sampled_at)} partitioned, and a
   * partitioned table's data file must carry exactly one partition value — the same rule {@code
   * IcebergLakeWriter}'s own {@code writeParquetByDay} follows), writes one Parquet file per day,
   * and commits every day's file in a single {@link Table#newAppend()}. No offset/frontier/
   * watermark property is ever stamped — see the class javadoc.
   */
  @Override
  public void writeBatch(final List<GaugeSample> batch) {
    if (batch.isEmpty()) {
      return;
    }
    final Map<Long, List<GaugeSample>> byDay = new LinkedHashMap<>();
    for (final GaugeSample sample : batch) {
      final long epochDay = Math.floorDiv(sample.sampledAtMs(), MILLIS_PER_DAY);
      byDay.computeIfAbsent(epochDay, ignored -> new ArrayList<>()).add(sample);
    }
    final AppendFiles append = table.newAppend();
    for (final Map.Entry<Long, List<GaugeSample>> dayGroup : byDay.entrySet()) {
      append.appendFile(writeParquetForDay(dayGroup.getKey(), dayGroup.getValue()));
    }
    append.commit();
  }

  private DataFile writeParquetForDay(final long epochDay, final List<GaugeSample> rows) {
    final String fileName = "gauge-" + UUID.randomUUID() + "-day" + epochDay + ".parquet";
    final String location = table.location() + "/data/" + fileName;
    final Path physicalPath = LocalFileIO.toFilesystemPath(location);
    try {
      if (physicalPath.getParent() != null) {
        Files.createDirectories(physicalPath.getParent());
      }
      try (Statement ddl = duckdb.createStatement()) {
        ddl.execute(STAGING_DDL);
      }
      final DuckDBConnection duckdbConnection = (DuckDBConnection) duckdb;
      try (DuckDBAppender appender = duckdbConnection.createAppender(STAGING_TABLE)) {
        for (final GaugeSample row : rows) {
          appender.beginRow();
          appender.append(row.sampledAtMs());
          appender.append(row.processId());
          appender.append(row.openInstances());
          appender.endRow();
        }
      }
      try (Statement copy = duckdb.createStatement()) {
        copy.execute("COPY (" + SELECT_SQL + ") TO '" + physicalPath + "' (FORMAT PARQUET)");
      }
      final long fileSizeBytes = Files.size(physicalPath);
      LOG.info(
          "Flushed {} open-instances-gauge row(s) (day {}) to {}",
          rows.size(),
          epochDay,
          physicalPath);
      return DataFiles.builder(table.spec())
          .withPath(location)
          .withFormat(FileFormat.PARQUET)
          .withPartitionValues(List.of(LocalDate.ofEpochDay(epochDay).toString()))
          .withRecordCount(rows.size())
          .withFileSizeInBytes(fileSizeBytes)
          .build();
    } catch (final SQLException | IOException e) {
      throw new IllegalStateException(
          "Failed to write open-instances-gauge Parquet file for day " + epochDay, e);
    }
  }
}
