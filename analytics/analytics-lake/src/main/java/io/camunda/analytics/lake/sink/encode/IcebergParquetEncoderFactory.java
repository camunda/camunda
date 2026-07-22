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
import java.util.concurrent.ThreadLocalRandom;
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
 * <p>Name uniqueness must hold across every writer that ever touches the table's directory — a
 * restarted process (which must never re-mint a name an earlier run committed: the encoder's output
 * file refuses to overwrite, so a collision fails the pipeline) and, once the app scales out,
 * <em>concurrent</em> processes on different machines each owning different source partitions but
 * writing the same tables. Neither counters nor clocks can guarantee that (restarts reset counters;
 * machines skew clocks), so each factory mints a random {@code writerId} at construction and every
 * file carries it: {@code <sequence>-<writerId>.parquet}. The zero-padded sequence keeps one
 * writer's files sortable and reproducible in tests; the writer id carries all the uniqueness — the
 * same reason every standard Iceberg writer embeds a UUID in its file names.
 */
public final class IcebergParquetEncoderFactory implements BatchEncoder.Factory {

  private static final String DEFAULT_COMPRESSION_CODEC = "zstd";

  private final Schema icebergSchema;
  private final FileSink fileSink;
  private final int targetRowGroupRows;
  private final Set<String> bloomFilterColumns;
  private final String compressionCodec;
  private final AtomicLong fileSequence = new AtomicLong();
  // Random per factory instance -- carries ALL the cross-writer/cross-restart uniqueness; see the
  // class javadoc's naming section.
  private final String writerId =
      "%012x".formatted(ThreadLocalRandom.current().nextLong() & 0xFFFFFFFFFFFFL);

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
    final String fileName = "%08d-%s.parquet".formatted(fileSequence.incrementAndGet(), writerId);
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
