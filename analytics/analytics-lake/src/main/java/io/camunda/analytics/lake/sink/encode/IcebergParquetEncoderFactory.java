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
 * <p>File naming: {@code day=<YYYY-MM-DD>/f-<sequence>-day<epochDay>.parquet}, relative to the
 * table's data location (see {@link FileSink#newOutputFile}'s javadoc). {@code epochDay} may be
 * negative (a day before 1970-01-01) — {@link LocalDate#ofEpochDay} handles that natively, so the
 * {@code YYYY-MM-DD} folder is always the real calendar day, never a "mixed" sentinel: every file
 * this factory opens carries exactly one family day (see {@link DayRouter}'s javadoc). The sequence
 * is a per-factory monotonic counter, not a random id, so file names stay reproducible in tests; it
 * does not need to be globally unique across factories/processes because {@link FileSink} owns the
 * table-scoped namespace each factory writes into.
 */
public final class IcebergParquetEncoderFactory implements BatchEncoder.Factory {

  private static final String DEFAULT_COMPRESSION_CODEC = "zstd";

  private final Schema icebergSchema;
  private final FileSink fileSink;
  private final int targetRowGroupRows;
  private final Set<String> bloomFilterColumns;
  private final String compressionCodec;
  private final AtomicLong fileSequence = new AtomicLong();

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
    final String dayFolder = LocalDate.ofEpochDay(epochDay).toString();
    final String fileName = "f-" + fileSequence.incrementAndGet() + "-day" + epochDay + ".parquet";
    final String relativePath = "day=" + dayFolder + "/" + fileName;
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
