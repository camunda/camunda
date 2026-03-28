/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.greatbearlake.exporter;

import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.protocol.record.Record;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.SimpleGroupFactory;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.example.ExampleParquetWriter;
import org.apache.parquet.hadoop.util.HadoopOutputFile;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName;
import org.apache.parquet.schema.Types;
import org.slf4j.Logger;

/**
 * Buffers Zeebe records and writes them as Apache Parquet files to HDFS once the configured batch
 * size is reached or an explicit flush is requested.
 *
 * <h3>At-least-once semantics</h3>
 *
 * This class is designed so that {@link io.camunda.zeebe.exporter.api.Exporter#export} can safely
 * throw on failure and let the broker retry the same record, without any data loss:
 *
 * <ul>
 *   <li><b>Buffer is never cleared on failure.</b> {@link #flush()} only clears the buffer after a
 *       successful HDFS write. If the write throws, the buffer retains all records so the next
 *       flush attempt (triggered by the broker retry or the periodic scheduler) operates on the
 *       full original batch.
 *   <li><b>{@link #add} is idempotent on position.</b> The Zeebe broker contract guarantees that if
 *       {@code export(record)} throws, the broker calls it again with the <em>exact same
 *       record</em> (same position) before advancing. {@link #add} detects this by tracking the
 *       last buffered position and skips re-adding a record with the same position, keeping the
 *       buffer stable across retries.
 * </ul>
 *
 * <p>Together these two invariants mean: on a successful flush the buffer is exactly the set of
 * records written to the parquet file; on failure the buffer is unchanged and the next attempt
 * writes the same set. The caller (the exporter) only calls {@code
 * controller.updateLastExportedRecordPosition()} after a flush returns without exception, so the
 * broker never advances past a record that was not durably written to HDFS.
 *
 * <h3>Duplicate files on restart</h3>
 *
 * If the JVM crashes after a successful HDFS write but before {@code
 * updateLastExportedRecordPosition} is called, the broker will re-deliver those records on restart.
 * This produces a second parquet file on HDFS with the same records — an expected consequence of
 * at-least-once delivery. Downstream deduplication (e.g. Iceberg's {@code MERGE INTO} or
 * position-based deduplication in Flink) handles this.
 *
 * <h3>Schema</h3>
 *
 * <table>
 *   <tr><th>Column</th><th>Parquet type</th><th>Iceberg type</th></tr>
 *   <tr><td>position</td><td>INT64</td><td>LongType</td></tr>
 *   <tr><td>partition_id</td><td>INT32</td><td>IntegerType</td></tr>
 *   <tr><td>key</td><td>INT64</td><td>LongType</td></tr>
 *   <tr><td>record_type</td><td>BINARY(UTF8)</td><td>StringType</td></tr>
 *   <tr><td>value_type</td><td>BINARY(UTF8)</td><td>StringType</td></tr>
 *   <tr><td>intent</td><td>BINARY(UTF8)</td><td>StringType</td></tr>
 *   <tr><td>timestamp</td><td>INT64(TIMESTAMP_MICROS, UTC)</td><td>TimestamptzType</td></tr>
 *   <tr><td>broker_version</td><td>BINARY(UTF8)</td><td>StringType</td></tr>
 *   <tr><td>json</td><td>BINARY(UTF8)</td><td>StringType</td></tr>
 * </table>
 */
class ParquetBatchWriter {

  static final MessageType SCHEMA =
      Types.buildMessage()
          .required(PrimitiveTypeName.INT64)
          .named("position")
          .required(PrimitiveTypeName.INT32)
          .named("partition_id")
          .required(PrimitiveTypeName.INT64)
          .named("key")
          .required(PrimitiveTypeName.BINARY)
          .as(LogicalTypeAnnotation.stringType())
          .named("record_type")
          .required(PrimitiveTypeName.BINARY)
          .as(LogicalTypeAnnotation.stringType())
          .named("value_type")
          .required(PrimitiveTypeName.BINARY)
          .as(LogicalTypeAnnotation.stringType())
          .named("intent")
          .required(PrimitiveTypeName.INT64)
          .as(
              LogicalTypeAnnotation.timestampType(
                  /* isAdjustedToUTC */ true, LogicalTypeAnnotation.TimeUnit.MICROS))
          .named("timestamp")
          .required(PrimitiveTypeName.BINARY)
          .as(LogicalTypeAnnotation.stringType())
          .named("broker_version")
          .required(PrimitiveTypeName.BINARY)
          .as(LogicalTypeAnnotation.stringType())
          .named("json")
          .named("zeebe_record");

  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
  private static final SimpleGroupFactory GROUP_FACTORY = new SimpleGroupFactory(SCHEMA);

  private final ParquetExporterConfiguration configuration;
  private final Configuration hadoopConf;
  private final Logger log;
  private final List<Group> buffer;

  /**
   * Position of the last record successfully added to the buffer. Used by {@link #add} to detect
   * broker retries: the broker calls {@code export(record)} again with the same record when the
   * previous call threw, so this guard prevents adding the same record twice.
   */
  private long lastAddedPosition = Long.MIN_VALUE;

  private long batchSequence = 0;

  ParquetBatchWriter(
      final ParquetExporterConfiguration configuration,
      final Configuration hadoopConf,
      final Logger log) {
    this.configuration = configuration;
    this.hadoopConf = hadoopConf;
    this.log = log;
    this.buffer = new ArrayList<>(configuration.getBatch().getSize());
  }

  /**
   * Adds {@code record} to the internal buffer.
   *
   * <p>This method is <strong>idempotent on position</strong>: if called a second time with a
   * record whose position equals the last buffered position (which happens when the broker retries
   * an {@code export()} call after a failed flush), the record is silently ignored. This prevents
   * the buffer from growing on retries and guarantees that each logical record appears exactly once
   * in the next flush.
   *
   * <p>The full JSON is captured immediately via {@link Record#toJson()} since the underlying
   * record buffer may be reused after this call returns.
   */
  void add(final Record<?> record) {
    if (record.getPosition() == lastAddedPosition) {
      // Same record delivered again — broker is retrying a failed export(); skip re-adding.
      return;
    }

    final var group = GROUP_FACTORY.newGroup();
    group.add("position", record.getPosition());
    group.add("partition_id", record.getPartitionId());
    group.add("key", record.getKey());
    group.add("record_type", Binary.fromString(record.getRecordType().name()));
    group.add("value_type", Binary.fromString(record.getValueType().name()));
    group.add("intent", Binary.fromString(intentName(record)));
    // Zeebe timestamp is milliseconds; Iceberg TIMESTAMP_MICROS expects microseconds.
    group.add("timestamp", record.getTimestamp() * 1_000L);
    group.add("broker_version", Binary.fromString(record.getBrokerVersion()));
    group.add("json", Binary.fromString(record.toJson()));

    buffer.add(group);
    lastAddedPosition = record.getPosition();
  }

  /** Returns {@code true} when the buffer has reached the configured batch size. */
  boolean isFull() {
    return buffer.size() >= configuration.getBatch().getSize();
  }

  /** Returns {@code true} when there are no records in the buffer. */
  boolean isEmpty() {
    return buffer.isEmpty();
  }

  /** Returns the number of records currently held in the buffer. */
  int size() {
    return buffer.size();
  }

  /**
   * Writes the current buffer to a new parquet file on HDFS.
   *
   * <p><strong>At-least-once contract:</strong> the buffer is cleared and {@code batchSequence} is
   * advanced <em>only after</em> the HDFS write completes successfully. If the write throws, this
   * method propagates the exception with the buffer unchanged. The caller must not call {@code
   * controller.updateLastExportedRecordPosition()} when this method throws, so the broker will
   * re-deliver the records.
   *
   * <p>This is a no-op when the buffer is empty.
   *
   * @throws ExporterException wrapping any {@link IOException} from the HDFS write
   */
  void flush() {
    if (buffer.isEmpty()) {
      return;
    }

    final var hdfsPath = resolveHdfsPath();
    try {
      ensureParentDirectory(hdfsPath);
      writeParquetFile(hdfsPath);
      // ---- success path only ----
      log.debug("Wrote {} records to {}", buffer.size(), hdfsPath);
      buffer.clear();
      batchSequence++;
    } catch (final IOException e) {
      // Buffer intentionally NOT cleared — records are retained for the next flush attempt.
      throw new ExporterException(
          "Failed to write parquet batch to "
              + hdfsPath
              + ". Buffer retains "
              + buffer.size()
              + " records for retry.",
          e);
    }
  }

  // ---- private helpers ----

  private Path resolveHdfsPath() {
    final int partitionId = buffer.getFirst().getInteger("partition_id", 0);
    final String date = LocalDate.now(ZoneOffset.UTC).format(DATE_FORMATTER);
    final long epochMs = System.currentTimeMillis();
    final String filename =
        String.format("partition-%d-batch-%d-%d.parquet", partitionId, batchSequence, epochMs);
    return new Path(configuration.getOutputPath(), "date=" + date + "/" + filename);
  }

  private void ensureParentDirectory(final Path path) throws IOException {
    final FileSystem fs = path.getFileSystem(hadoopConf);
    fs.mkdirs(path.getParent());
  }

  /**
   * Writes the current buffer contents to a parquet file at {@code path}.
   *
   * <p>Protected (not private) to allow subclasses in tests to inject write failures without
   * requiring a real HDFS cluster.
   */
  protected void writeParquetFile(final Path path) throws IOException {
    final var outputFile = HadoopOutputFile.fromPath(path, hadoopConf);
    try (final ParquetWriter<Group> writer =
        ExampleParquetWriter.builder(outputFile).withType(SCHEMA).withConf(hadoopConf).build()) {
      for (final var group : buffer) {
        writer.write(group);
      }
    }
  }

  private static String intentName(final Record<?> record) {
    final var intent = record.getIntent();
    return intent instanceof Enum<?> e ? e.name() : intent.toString();
  }
}
