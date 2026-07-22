/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.encode;

import io.camunda.analytics.lake.sink.BatchEncoder;
import io.camunda.analytics.lake.sink.DataFileResult;
import io.camunda.analytics.lake.sink.SortedRun;
import io.camunda.analytics.lake.sink.TableSchema;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import org.apache.iceberg.Schema;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.data.parquet.GenericParquetWriter;
import org.apache.iceberg.io.FileAppender;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.parquet.Parquet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link BatchEncoder} rung 1: one open Parquet file, written through iceberg-parquet's {@link
 * Parquet.WriteBuilder} configured with {@link GenericParquetWriter} (iceberg-data's generic writer
 * function) over a single reused {@link BatchRowView}. Field ids in the file, per-column metrics
 * and bloom filters all come from the library given this configuration; nothing here re-derives
 * them.
 *
 * <h2>Row-group alignment (best-effort)</h2>
 *
 * <p>Parquet-mr's public writer API only ever closes a row group from inside {@code
 * InternalParquetRecordWriter#checkBlockSizeReached()}, triggered by its OWN periodic check of
 * buffered byte size against a configured threshold — there is no public hook on the {@link
 * FileAppender}/{@code ParquetWriter} facade iceberg-parquet hands back to force a row-group
 * boundary at an arbitrary row (that capability, {@code flushRowGroupToStore()}, exists only on the
 * package-private internal writer). Exact "one append = one row group" alignment would require
 * driving parquet-column's {@code ColumnWriteStore} directly — the "rung 1.5" escape hatch {@link
 * BatchEncoder}'s own class javadoc anticipates — and is out of scope here.
 *
 * <p>What this rung does instead: {@link TableProperties#PARQUET_ROW_GROUP_SIZE_BYTES} is pinned to
 * a 1-byte floor (so the byte-size half of the check is always satisfied once any row is buffered)
 * and both {@link TableProperties#PARQUET_ROW_GROUP_CHECK_MIN_RECORD_COUNT} and {@link
 * TableProperties#PARQUET_ROW_GROUP_CHECK_MAX_RECORD_COUNT} are pinned to {@code
 * targetRowGroupRows}, which forces the periodic check itself (not the byte threshold) to be the
 * deciding factor and pins its cadence to a fixed record count. Verified empirically (writing 6
 * rows with {@code targetRowGroupRows = 3} produces exactly 2 row groups of 3 rows each): when
 * every append happens to be exactly {@code targetRowGroupRows} rows, the row-group boundaries land
 * exactly on the append boundaries. When an append is smaller or larger than {@code
 * targetRowGroupRows} (a day-boundary split, a small tail flush, ...), the periodic cadence and the
 * append boundary drift apart — the row-group count then approximates, rather than exactly matches,
 * the number of appended ranges. Callers that want tight alignment should therefore size segments
 * close to {@code targetRowGroupRows}, per {@link BatchEncoder}'s own class javadoc.
 */
final class IcebergParquetEncoder implements BatchEncoder {

  private static final Logger LOG = LoggerFactory.getLogger(IcebergParquetEncoder.class);

  /**
   * Deliberately tiny: makes the row-group byte-size check always pass, so the check cadence
   * (pinned to {@code targetRowGroupRows} below) is what actually decides row-group boundaries. See
   * the class javadoc's "Row-group alignment" section.
   */
  private static final String ROW_GROUP_SIZE_BYTES_FLOOR = "1";

  private final String table;
  private final String path;
  private final long epochDay;
  private final FileAppender<Record> appender;
  private final BatchRowView view;

  private long rowCount;
  private boolean finished;
  private boolean aborted;

  IcebergParquetEncoder(
      final TableSchema tableSchema,
      final Schema icebergSchema,
      final OutputFile outputFile,
      final int targetRowGroupRows,
      final Set<String> bloomFilterColumns,
      final String compressionCodec,
      final long epochDay) {
    table = tableSchema.table();
    path = outputFile.location();
    this.epochDay = epochDay;
    view = new BatchRowView(tableSchema, icebergSchema.asStruct());

    final int rowGroupCheckRecordCount = Math.max(1, targetRowGroupRows);
    final Parquet.WriteBuilder builder =
        Parquet.write(outputFile)
            .schema(icebergSchema)
            .createWriterFunc(GenericParquetWriter::create)
            .set(TableProperties.PARQUET_COMPRESSION, compressionCodec)
            .set(TableProperties.PARQUET_ROW_GROUP_SIZE_BYTES, ROW_GROUP_SIZE_BYTES_FLOOR)
            .set(
                TableProperties.PARQUET_ROW_GROUP_CHECK_MIN_RECORD_COUNT,
                Integer.toString(rowGroupCheckRecordCount))
            .set(
                TableProperties.PARQUET_ROW_GROUP_CHECK_MAX_RECORD_COUNT,
                Integer.toString(rowGroupCheckRecordCount));
    // Dictionary encoding is on by default in parquet-mr (ParquetProperties.DEFAULT_IS_DICTIONARY_
    // ENABLED); nothing to configure to get it.
    for (final String column : bloomFilterColumns) {
      builder.set(TableProperties.PARQUET_BLOOM_FILTER_COLUMN_ENABLED_PREFIX + column, "true");
    }
    try {
      appender = builder.build();
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to open Parquet writer for " + path, e);
    }
  }

  @Override
  public void append(final SortedRun run, final int fromIndex, final int toIndex) {
    if (finished || aborted) {
      throw new IllegalStateException("encoder for " + path + " is already finished/aborted");
    }
    for (int i = fromIndex; i < toIndex; i++) {
      view.moveTo(run, i);
      appender.add(view);
      rowCount++;
    }
  }

  @Override
  public DataFileResult finish() {
    if (finished) {
      throw new IllegalStateException("encoder for " + path + " already finished");
    }
    if (aborted) {
      throw new IllegalStateException("encoder for " + path + " was aborted, cannot finish");
    }
    try {
      appender.close();
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to close Parquet writer for " + path, e);
    }
    finished = true;
    return new DataFileResult(
        table, path, rowCount, appender.length(), appender.metrics(), epochDay);
  }

  @Override
  public void abort() {
    // Idempotent by design (BatchEncoder's contract: "safe to call after a failed append") and
    // safe after finish() too, rather than throwing, since a shutdown path may not know which
    // encoders already finished. Never deletes the partially-written file: per the sink package's
    // own crash contract (see FileSink's javadoc), a file is worthless until its descriptor is
    // committed, and an orphan sweep -- not this method -- is what reclaims it.
    if (finished || aborted) {
      return;
    }
    aborted = true;
    try {
      appender.close();
    } catch (final IOException e) {
      LOG.warn("Failed to close Parquet writer for {} during abort", path, e);
    }
  }
}
