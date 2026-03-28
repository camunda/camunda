/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.greatbearlake.exporter;

import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import java.time.Duration;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Zeebe exporter that writes records as Apache Parquet files directly to HDFS.
 *
 * <p>Records are buffered in memory and flushed to a new parquet file on HDFS either when the batch
 * size limit ({@link ParquetExporterConfiguration.BatchConfiguration#size}) is reached or after the
 * delay ({@link ParquetExporterConfiguration.BatchConfiguration#delay}) has elapsed without a full
 * batch being produced.
 *
 * <p>Each parquet file is placed at:
 *
 * <pre>
 * {outputPath}/date={yyyy-MM-dd}/partition-{id}-batch-{seq}-{epochMs}.parquet
 * </pre>
 *
 * <p>The Hive-style {@code date=} directory lets a downstream Flink job discover the files and
 * register them as Apache Iceberg tables backed by the same HDFS paths. Trino then queries those
 * Iceberg tables via the Hive Metastore.
 *
 * <h3>Enabling in the broker</h3>
 *
 * <pre>{@code
 * exporters:
 *   parquet:
 *     className: io.camunda.greatbearlake.exporter.ParquetExporter
 *     args:
 *       hdfsUri: hdfs://namenode:9000
 *       outputPath: /zeebe/export
 *       hadoopConfigPath: /etc/hadoop/conf   # optional
 *       batch:
 *         size: 1000
 *         delay: 5
 * }</pre>
 */
public class ParquetExporter implements Exporter {

  private Logger log = LoggerFactory.getLogger(getClass().getPackageName());

  private Controller controller;
  private ParquetExporterConfiguration configuration;
  private ParquetBatchWriter batchWriter;

  private long lastPosition = -1L;

  @Override
  public void configure(final Context context) {
    log = context.getLogger();
    configuration = context.getConfiguration().instantiate(ParquetExporterConfiguration.class);
    log.debug("Parquet exporter configured with {}", configuration);
    validate(configuration);
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;
    final var hadoopConf = buildHadoopConfiguration(configuration);
    batchWriter = createBatchWriter(configuration, hadoopConf, log);
    scheduleDelayedFlush();
    log.info(
        "Parquet exporter opened, writing to {}{}",
        configuration.getHdfsUri(),
        configuration.getOutputPath());
  }

  @Override
  public void close() {
    if (batchWriter != null) {
      try {
        flushIfNotEmpty();
        updateLastExportedPosition();
      } catch (final Exception e) {
        // Flush failed: position is NOT updated. The broker will re-deliver the buffered
        // records (batchWriter.size() of them) from the last committed position on restart.
        log.warn(
            "Failed to flush {} buffered records on close — they will be re-exported after"
                + " restart.",
            batchWriter.size(),
            e);
      }
      // No batchWriter.close() call: ParquetBatchWriter holds no persistent resources and
      // calling flush() a second time here would produce an orphan HDFS file for records
      // whose position was never committed to the controller.
    }
    log.info("Parquet exporter closed");
  }

  @Override
  public void export(final Record<?> record) {
    batchWriter.add(record);
    lastPosition = record.getPosition();

    if (batchWriter.isFull()) {
      batchWriter.flush();
      updateLastExportedPosition();
    }
  }

  // ---- extensibility seam (protected for testing) ----

  /**
   * Factory method for the batch writer. Overriding this in tests allows a failure-injecting
   * subclass to be substituted without touching private state.
   */
  protected ParquetBatchWriter createBatchWriter(
      final ParquetExporterConfiguration config,
      final Configuration hadoopConf,
      final Logger logger) {
    return new ParquetBatchWriter(config, hadoopConf, logger);
  }

  // ---- private helpers ----

  private static void validate(final ParquetExporterConfiguration config) {
    if (config.getHdfsUri() == null || config.getHdfsUri().isBlank()) {
      throw new ExporterException(
          "Parquet exporter hdfsUri must not be null or blank. "
              + "Set 'args.hdfsUri' in the exporter configuration.");
    }
    if (config.getOutputPath() == null || config.getOutputPath().isBlank()) {
      throw new ExporterException(
          "Parquet exporter outputPath must not be null or blank. "
              + "Set 'args.outputPath' in the exporter configuration.");
    }
    if (config.getBatch().getSize() < 1) {
      throw new ExporterException(
          "Parquet exporter batch.size must be >= 1. Current value: "
              + config.getBatch().getSize());
    }
    if (config.getBatch().getDelay() < 1) {
      throw new ExporterException(
          "Parquet exporter batch.delay must be >= 1 second. Current value: "
              + config.getBatch().getDelay());
    }
  }

  /**
   * Builds a Hadoop {@link Configuration} from the exporter configuration.
   *
   * <ol>
   *   <li>If {@link ParquetExporterConfiguration#hadoopConfigPath} is set, {@code core-site.xml}
   *       and {@code hdfs-site.xml} from that directory are loaded first.
   *   <li>{@code fs.defaultFS} is always set to {@link ParquetExporterConfiguration#hdfsUri},
   *       overriding any value from the XML files.
   *   <li>{@code dfs.client.use.datanode.hostname} is enabled so that HDFS DataNode addresses
   *       resolve correctly inside Docker networks (the DataNode advertises its hostname, not its
   *       container IP).
   * </ol>
   */
  static Configuration buildHadoopConfiguration(final ParquetExporterConfiguration config) {
    final var conf = new Configuration();

    if (config.getHadoopConfigPath() != null && !config.getHadoopConfigPath().isBlank()) {
      conf.addResource(new Path(config.getHadoopConfigPath(), "core-site.xml"));
      conf.addResource(new Path(config.getHadoopConfigPath(), "hdfs-site.xml"));
    }

    // These two settings must come last so they always take effect.
    conf.set("fs.defaultFS", config.getHdfsUri());
    conf.set("dfs.client.use.datanode.hostname", "true");

    return conf;
  }

  private void flushAndReschedule() {
    try {
      flushIfNotEmpty();
      updateLastExportedPosition();
    } catch (final Exception e) {
      log.warn("Unexpected exception during periodic parquet flush, will retry later.", e);
    }
    scheduleDelayedFlush();
  }

  private void scheduleDelayedFlush() {
    controller.scheduleCancellableTask(
        Duration.ofSeconds(configuration.getBatch().getDelay()), this::flushAndReschedule);
  }

  private void flushIfNotEmpty() {
    if (!batchWriter.isEmpty()) {
      batchWriter.flush();
    }
  }

  private void updateLastExportedPosition() {
    if (lastPosition >= 0) {
      controller.updateLastExportedRecordPosition(lastPosition);
    }
  }
}
