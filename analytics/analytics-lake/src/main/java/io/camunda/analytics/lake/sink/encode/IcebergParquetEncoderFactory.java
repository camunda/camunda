/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.encode;

import io.camunda.analytics.lake.sink.BatchEncoder;
import io.camunda.analytics.lake.sink.FileSink;
import io.camunda.analytics.lake.sink.TableSchema;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.iceberg.Schema;
import org.apache.iceberg.io.OutputFile;

/**
 * {@link BatchEncoder.Factory} rung 1: opens one {@link IcebergParquetEncoder} per {@link
 * #newFile}, all writing to the same table (fixed {@link Schema}) through the same {@link
 * FileSink}.
 *
 * <p>File naming: {@code day=<YYYY-MM-DD>/<sequence>.parquet}, relative to the table's data
 * location (see {@link FileSink#newOutputFile}'s javadoc) — the folder carries the calendar day
 * (mirroring the tables' {@code days(...)} partition spec and nothing else; provenance like the
 * source partition deliberately does NOT appear in the layout: it lives in the commit journal and
 * the snapshot summaries, where it is queryable instead of parsed out of paths). {@code epochDay}
 * may be negative (a day before 1970-01-01) — {@link LocalDate#ofEpochDay} handles that natively,
 * so the day folder is always the real calendar day, never a "mixed" sentinel: every file this
 * factory opens carries exactly one family day (see {@link DayRouter}'s javadoc).
 *
 * <p>The sequence is zero-padded (sortable listings) and seeded from the factory's creation time in
 * epoch milliseconds rather than starting at zero: a factory only lives as long as its process, and
 * a restarted process must never re-mint a name an earlier run already committed — the encoder's
 * output file refuses to overwrite, so a colliding name would fail the pipeline. A forward-moving
 * clock makes every run's sequences disjoint without any directory scan or random id in the name.
 * Uniqueness is only needed within one table's directory (single writer per table; {@link FileSink}
 * owns that namespace).
 */
public final class IcebergParquetEncoderFactory implements BatchEncoder.Factory {

  private static final String DEFAULT_COMPRESSION_CODEC = "zstd";

  private final Schema icebergSchema;
  private final FileSink fileSink;
  private final int targetRowGroupRows;
  private final Set<String> bloomFilterColumns;
  private final String compressionCodec;
  // Seeded from wall clock, not zero -- see the class javadoc's naming section for why.
  private final AtomicLong fileSequence = new AtomicLong(System.currentTimeMillis());

  public IcebergParquetEncoderFactory(
      final Schema icebergSchema,
      final FileSink fileSink,
      final int targetRowGroupRows,
      final Set<String> bloomFilterColumns,
      final String compressionCodec) {
    this.icebergSchema = icebergSchema;
    this.fileSink = fileSink;
    this.targetRowGroupRows = targetRowGroupRows;
    this.bloomFilterColumns = Set.copyOf(bloomFilterColumns);
    this.compressionCodec = compressionCodec;
  }

  /** Same as the 5-arg constructor, defaulting the compression codec to {@code zstd}. */
  public IcebergParquetEncoderFactory(
      final Schema icebergSchema,
      final FileSink fileSink,
      final int targetRowGroupRows,
      final Set<String> bloomFilterColumns) {
    this(
        icebergSchema, fileSink, targetRowGroupRows, bloomFilterColumns, DEFAULT_COMPRESSION_CODEC);
  }

  @Override
  public BatchEncoder newFile(final TableSchema schema, final long epochDay) {
    final String fileName = "%014d.parquet".formatted(fileSequence.incrementAndGet());
    final String relativePath = "day=" + LocalDate.ofEpochDay(epochDay) + "/" + fileName;
    final OutputFile outputFile = fileSink.newOutputFile(schema, relativePath);
    return new IcebergParquetEncoder(
        schema,
        icebergSchema,
        outputFile,
        targetRowGroupRows,
        bloomFilterColumns,
        compressionCodec,
        epochDay);
  }
}
