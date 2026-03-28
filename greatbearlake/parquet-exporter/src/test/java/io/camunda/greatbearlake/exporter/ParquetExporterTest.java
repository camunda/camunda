/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.greatbearlake.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.example.GroupReadSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ParquetExporterTest {

  @TempDir Path tempDir;

  private ParquetExporter exporter;
  private ExporterTestController controller;
  private ExporterTestContext context;

  @BeforeEach
  void setUp() {
    exporter = new ParquetExporter();
    controller = new ExporterTestController();
    context = contextFor(localConfig(tempDir, 5, 60));
  }

  // ---- configuration validation ----

  @Test
  void shouldRejectBlankHdfsUri() {
    final var bad = localConfig(tempDir, 5, 60);
    bad.hdfsUri = "  ";
    assertThatThrownBy(() -> exporter.configure(contextFor(bad)))
        .isInstanceOf(ExporterException.class)
        .hasMessageContaining("hdfsUri");
  }

  @Test
  void shouldRejectBlankOutputPath() {
    final var bad = localConfig(tempDir, 5, 60);
    bad.outputPath = "";
    assertThatThrownBy(() -> exporter.configure(contextFor(bad)))
        .isInstanceOf(ExporterException.class)
        .hasMessageContaining("outputPath");
  }

  @Test
  void shouldRejectZeroBatchSize() {
    assertThatThrownBy(() -> exporter.configure(contextFor(localConfig(tempDir, 0, 60))))
        .isInstanceOf(ExporterException.class)
        .hasMessageContaining("batch.size");
  }

  // ---- normal operation ----

  @Test
  void shouldFlushWhenBatchSizeReached() throws Exception {
    exporter.configure(context);
    exporter.open(controller);

    exportRecords(exporter, 5);

    final var files = listParquetFiles();
    assertThat(files).hasSize(1);
    assertThat(readParquetFile(files.get(0))).hasSize(5);
  }

  @Test
  void shouldWriteCorrectMetadataColumns() throws Exception {
    exporter.configure(context);
    exporter.open(controller);

    exportRecords(exporter, 5);

    final var row = readParquetFile(listParquetFiles().get(0)).get(0);
    assertThat(row.getBinary("value_type", 0).toStringUsingUTF8()).isEqualTo("PROCESS_INSTANCE");
    assertThat(row.getBinary("record_type", 0).toStringUsingUTF8()).isEqualTo("EVENT");
    assertThat(row.getBinary("intent", 0).toStringUsingUTF8()).isEqualTo("ELEMENT_ACTIVATING");
    assertThat(row.getInteger("partition_id", 0)).isEqualTo(1);
    assertThat(row.getBinary("broker_version", 0).toStringUsingUTF8()).isEqualTo("8.10.0");
    assertThat(row.getBinary("json", 0).toStringUsingUTF8()).isNotBlank();
  }

  @Test
  void shouldStoreTimestampInMicroseconds() throws Exception {
    exporter.configure(context);
    exporter.open(controller);

    exportRecords(exporter, 5);

    final var row = readParquetFile(listParquetFiles().get(0)).get(0);
    assertThat(row.getLong("timestamp", 0)).isEqualTo(1_000_000L * 1_000L);
  }

  @Test
  void shouldPlaceFilesInsideDatePartitionDirectory() throws Exception {
    exporter.configure(context);
    exporter.open(controller);

    exportRecords(exporter, 5);

    final var file = listParquetFiles().get(0);
    assertThat(file.getParent().getFileName().toString()).matches("^date=\\d{4}-\\d{2}-\\d{2}$");
  }

  @Test
  void shouldProduceOneFilePerFullBatch() throws Exception {
    exporter.configure(context);
    exporter.open(controller);

    exportRecords(exporter, 10);

    assertThat(listParquetFiles()).hasSize(2);
  }

  @Test
  void shouldUpdateLastExportedPositionOnlyAfterSuccessfulFlush() throws Exception {
    exporter.configure(context);
    exporter.open(controller);

    exportRecords(exporter, 4);
    assertThat(controller.getLastExportedRecordPosition()).isEqualTo(-1L);

    exporter.export(mockRecord(4));
    assertThat(controller.getLastExportedRecordPosition()).isEqualTo(4L);
  }

  @Test
  void shouldFlushRemainingRecordsOnClose() throws Exception {
    exporter.configure(context);
    exporter.open(controller);

    exporter.export(mockRecord(0));
    exporter.export(mockRecord(1));
    assertThat(listParquetFiles()).isEmpty();

    exporter.close();

    assertThat(listParquetFiles()).hasSize(1);
    assertThat(readParquetFile(listParquetFiles().get(0))).hasSize(2);
  }

  // ---- helpers ----

  private static void exportRecords(final ParquetExporter exp, final int count) {
    for (int i = 0; i < count; i++) {
      exp.export(mockRecord(i));
    }
  }

  private List<Path> listParquetFiles() throws IOException {
    try (final var stream = Files.walk(tempDir)) {
      return stream.filter(p -> p.toString().endsWith(".parquet")).sorted().toList();
    }
  }

  private static List<Group> readParquetFile(final Path localPath) throws IOException {
    final var conf = new Configuration();
    conf.set("fs.defaultFS", "file:///");
    final var hadoopPath = new org.apache.hadoop.fs.Path(localPath.toUri());
    final var results = new ArrayList<Group>();
    try (final ParquetReader<Group> reader =
        ParquetReader.builder(new GroupReadSupport(), hadoopPath).withConf(conf).build()) {
      Group row;
      while ((row = reader.read()) != null) {
        results.add(row);
      }
    }
    return results;
  }

  @SuppressWarnings("unchecked")
  static Record<?> mockRecord(final long position) {
    final var record = mock(Record.class);
    when(record.getPosition()).thenReturn(position);
    when(record.getPartitionId()).thenReturn(1);
    when(record.getKey()).thenReturn(position * 10L);
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.PROCESS_INSTANCE);
    when(record.getIntent()).thenReturn(ProcessInstanceIntent.ELEMENT_ACTIVATING);
    when(record.getTimestamp()).thenReturn(1_000_000L);
    when(record.getBrokerVersion()).thenReturn("8.10.0");
    when(record.toJson()).thenReturn("{\"position\":" + position + "}");
    return record;
  }

  static ParquetExporterConfiguration localConfig(
      final Path dir, final int batchSize, final int delaySeconds) {
    final var config = new ParquetExporterConfiguration();
    config.hdfsUri = "file:///";
    config.outputPath = dir.toAbsolutePath().toString();
    config.batch.size = batchSize;
    config.batch.delay = delaySeconds;
    return config;
  }

  private static ExporterTestContext contextFor(final ParquetExporterConfiguration config) {
    return new ExporterTestContext()
        .setConfiguration(new ExporterTestConfiguration<>("parquet", config));
  }

  /**
   * Returns a {@link ParquetBatchWriter} whose {@link ParquetBatchWriter#writeParquetFile} throws
   * an {@link IOException} for the first {@code failCount} calls and then delegates to the real
   * implementation. This lets tests simulate transient HDFS failures without a real cluster.
   */
  private static ParquetBatchWriter writerWithFailures(final Path dir, final int failCount) {
    final var config = localConfig(dir, 5, 60);
    final var hadoopConf = ParquetExporter.buildHadoopConfiguration(config);
    final Logger log = LoggerFactory.getLogger("test");
    return new ParquetBatchWriter(config, hadoopConf, log) {
      private int remaining = failCount;

      @Override
      protected void writeParquetFile(final org.apache.hadoop.fs.Path path) throws IOException {
        if (remaining > 0) {
          remaining--;
          throw new IOException("simulated HDFS write failure");
        }
        super.writeParquetFile(path);
      }
    };
  }

  /**
   * Returns a {@link ParquetExporter} that uses a writer which fails for the first {@code
   * failCount} writes. Uses the {@link ParquetExporter#createBatchWriter} seam so no private state
   * is accessed.
   */
  private ParquetExporter exporterWithFailures(final Path dir, final int failCount) {
    return new ParquetExporter() {
      @Override
      protected ParquetBatchWriter createBatchWriter(
          final ParquetExporterConfiguration config,
          final Configuration hadoopConf,
          final Logger logger) {
        return new ParquetBatchWriter(config, hadoopConf, logger) {
          private int remaining = failCount;

          @Override
          protected void writeParquetFile(final org.apache.hadoop.fs.Path path) throws IOException {
            if (remaining > 0) {
              remaining--;
              throw new IOException("simulated HDFS write failure");
            }
            super.writeParquetFile(path);
          }
        };
      }
    };
  }

  // ---- at-least-once semantics ----

  @Nested
  final class AtLeastOnce {

    /**
     * If the HDFS write fails the buffer must be retained intact so that the next flush attempt
     * re-sends exactly the same records. Silently clearing the buffer on failure would cause data
     * loss for all records buffered before the failing one.
     */
    @Test
    void shouldRetainBufferWhenFlushFails() {
      final var writer = writerWithFailures(tempDir, 1);

      writer.add(mockRecord(0));
      writer.add(mockRecord(1));
      writer.add(mockRecord(2));

      assertThatThrownBy(writer::flush).isInstanceOf(ExporterException.class);

      assertThat(writer.size()).isEqualTo(3);
    }

    /**
     * After a flush failure, the next attempt must write the full original batch — no records lost,
     * no silent discard.
     */
    @Test
    void shouldWriteAllRecordsOnRetryAfterFlushFailure() throws Exception {
      final var writer = writerWithFailures(tempDir, 1);

      writer.add(mockRecord(0));
      writer.add(mockRecord(1));
      writer.add(mockRecord(2));

      assertThatThrownBy(writer::flush).isInstanceOf(ExporterException.class);

      writer.flush(); // retry succeeds
      assertThat(listParquetFiles()).hasSize(1);
      assertThat(readParquetFile(listParquetFiles().get(0))).hasSize(3);
    }

    /**
     * The Zeebe broker calls {@code export(record)} again with the exact same record when the
     * previous call threw. {@link ParquetBatchWriter#add} must ignore a second call for the same
     * position to prevent the buffer from growing on every broker retry.
     */
    @Test
    void shouldIgnoreReAddOfSamePositionOnBrokerRetry() {
      final var writer = writerWithFailures(tempDir, 1);

      writer.add(mockRecord(0));
      writer.add(mockRecord(1));

      assertThatThrownBy(writer::flush).isInstanceOf(ExporterException.class);

      // Broker retries export(record(1)) — same position, must be a no-op
      writer.add(mockRecord(1));

      assertThat(writer.size()).isEqualTo(2); // still 2, not 3
    }

    /**
     * Full-stack at-least-once flow through {@link ParquetExporter#export}:
     *
     * <ol>
     *   <li>Records 0–3 buffered; no flush.
     *   <li>Record 4 fills the batch; flush fails → {@code export()} throws.
     *   <li>Position is NOT committed.
     *   <li>Broker retries {@code export(record_4)}; flush succeeds.
     *   <li>Exactly 5 records land in one parquet file; position committed to 4.
     * </ol>
     */
    @Test
    void shouldRecoverFromFailedExportAndWriteExactlyOnce() throws Exception {
      final var testExporter = exporterWithFailures(tempDir, 1);
      testExporter.configure(contextFor(localConfig(tempDir, 5, 60)));
      testExporter.open(controller);

      for (int i = 0; i < 4; i++) {
        testExporter.export(mockRecord(i));
      }
      assertThat(controller.getLastExportedRecordPosition()).isEqualTo(-1L);

      // Record 4 fills the batch; flush fails
      assertThatThrownBy(() -> testExporter.export(mockRecord(4)))
          .isInstanceOf(ExporterException.class);

      assertThat(controller.getLastExportedRecordPosition()).isEqualTo(-1L);
      assertThat(listParquetFiles()).isEmpty();

      // Broker retries the same record — flush succeeds on second attempt
      testExporter.export(mockRecord(4));

      assertThat(controller.getLastExportedRecordPosition()).isEqualTo(4L);
      assertThat(listParquetFiles()).hasSize(1);
      assertThat(readParquetFile(listParquetFiles().get(0))).hasSize(5);
    }

    /**
     * When {@code close()} cannot flush, the controller position must NOT advance. On restart the
     * broker will re-deliver the buffered records from the last committed position.
     */
    @Test
    void shouldNotCommitPositionWhenCloseFails() throws Exception {
      final var testExporter = exporterWithFailures(tempDir, Integer.MAX_VALUE);
      testExporter.configure(contextFor(localConfig(tempDir, 10, 60)));
      testExporter.open(controller);

      for (int i = 0; i < 3; i++) {
        testExporter.export(mockRecord(i));
      }
      assertThat(controller.getLastExportedRecordPosition()).isEqualTo(-1L);

      testExporter.close(); // flush fails silently inside close()

      assertThat(controller.getLastExportedRecordPosition()).isEqualTo(-1L);
      assertThat(listParquetFiles()).isEmpty();
    }
  }
}
