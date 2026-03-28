/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.greatbearlake.exporter;

/**
 * Configuration for the {@link ParquetExporter}.
 *
 * <p>The exporter writes parquet files directly to HDFS using the Hadoop FileSystem API. Files are
 * laid out with Hive-style date partitioning so that a downstream Flink job can register them as
 * Apache Iceberg tables and expose them to Trino.
 *
 * <h3>Output path layout</h3>
 *
 * <pre>
 * {outputPath}/date={yyyy-MM-dd}/partition-{id}-batch-{seq}-{epochMs}.parquet
 * </pre>
 *
 * <h3>Example broker configuration</h3>
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
public class ParquetExporterConfiguration {

  /**
   * HDFS Namenode URI, e.g. {@code hdfs://namenode:9000}.
   *
   * <p>Set to {@code file:///} for local-filesystem writes during development or testing.
   */
  public String hdfsUri = "hdfs://namenode:9000";

  /**
   * Absolute HDFS path under which parquet files are written, e.g. {@code /zeebe/export}.
   *
   * <p>Sub-directories are created automatically on first write.
   */
  public String outputPath = "/zeebe/export";

  /**
   * Optional path to a directory that contains {@code core-site.xml} and/or {@code hdfs-site.xml}.
   * When set, these files are loaded into the Hadoop {@code Configuration} before any other
   * settings so that site-specific properties (replication factor, block size, etc.) are honoured.
   *
   * <p>Leave {@code null} (the default) to rely solely on {@link #hdfsUri} and the built-in
   * defaults.
   */
  public String hadoopConfigPath = null;

  public final BatchConfiguration batch = new BatchConfiguration();

  public String getHdfsUri() {
    return hdfsUri;
  }

  public void setHdfsUri(final String hdfsUri) {
    this.hdfsUri = hdfsUri;
  }

  public String getOutputPath() {
    return outputPath;
  }

  public void setOutputPath(final String outputPath) {
    this.outputPath = outputPath;
  }

  public String getHadoopConfigPath() {
    return hadoopConfigPath;
  }

  public void setHadoopConfigPath(final String hadoopConfigPath) {
    this.hadoopConfigPath = hadoopConfigPath;
  }

  public BatchConfiguration getBatch() {
    return batch;
  }

  @Override
  public String toString() {
    return "ParquetExporterConfiguration{"
        + "hdfsUri='"
        + hdfsUri
        + '\''
        + ", outputPath='"
        + outputPath
        + '\''
        + ", hadoopConfigPath='"
        + hadoopConfigPath
        + '\''
        + ", batch="
        + batch
        + '}';
  }

  /** Controls batch flushing behaviour. */
  public static class BatchConfiguration {

    /**
     * Maximum number of records to buffer before writing a parquet file. When this limit is reached
     * the batch is flushed immediately.
     */
    public int size = 1_000;

    /**
     * Maximum number of seconds to wait before flushing a non-empty batch regardless of its size.
     * This guarantees that records are written to HDFS even during low-throughput periods.
     */
    public int delay = 5;

    public int getSize() {
      return size;
    }

    public void setSize(final int size) {
      this.size = size;
    }

    public int getDelay() {
      return delay;
    }

    public void setDelay(final int delay) {
      this.delay = delay;
    }

    @Override
    public String toString() {
      return "BatchConfiguration{size=" + size + ", delay=" + delay + '}';
    }
  }
}
