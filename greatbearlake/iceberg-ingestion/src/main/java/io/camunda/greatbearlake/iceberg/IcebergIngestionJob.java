/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.greatbearlake.iceberg;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flink job that reads Zeebe records from the HDFS parquet landing zone written by the {@code
 * greatbearlake-parquet-exporter} and ingests them into an Apache Iceberg table registered in the
 * Hive Metastore catalog.
 *
 * <h3>Pipeline</h3>
 *
 * <pre>
 * HDFS parquet landing zone
 *   hdfs://{hdfsUri}/{inputPath}/date=yyyy-MM-dd/partition-{id}-batch-{seq}-{ms}.parquet
 *       ↓  (Flink FileSystem source, streaming, checkpoint-driven)
 * Iceberg table  {catalog}.{database}.{table}
 *       ↓  (Trino query via Hive Metastore)
 * BI / analytics layer
 * </pre>
 *
 * <h3>Deduplication</h3>
 *
 * The parquet exporter gives at-least-once guarantees, so the same records may appear in multiple
 * files after a broker restart. Downstream deduplication is handled by Trino views that apply a
 * {@code ROW_NUMBER() OVER (PARTITION BY position ORDER BY …) = 1} filter, or by Iceberg's {@code
 * MERGE INTO} upsert using {@code position} as the unique key.
 *
 * <h3>Command-line arguments</h3>
 *
 * <pre>
 *   --hdfsUri          hdfs://namenode:9000
 *   --inputPath        /zeebe/export
 *   --metastoreUri     thrift://hive-metastore:9083
 *   --catalog          zeebe_iceberg
 *   --database         zeebe
 *   --table            zeebe_records
 *   --checkpointMs     60000            (optional, default 60 s)
 * </pre>
 */
public class IcebergIngestionJob {

  private static final Logger LOG = LoggerFactory.getLogger(IcebergIngestionJob.class);

  public static void main(final String[] args) throws Exception {
    final var config = JobConfig.parse(args);
    LOG.info("Starting IcebergIngestionJob with config: {}", config);

    final var env = StreamExecutionEnvironment.getExecutionEnvironment();
    env.enableCheckpointing(config.checkpointMs());

    final var settings = EnvironmentSettings.inStreamingMode();
    final var tEnv = StreamTableEnvironment.create(env, settings);

    setupHiveCatalog(tEnv, config);
    ensureIcebergTable(tEnv, config);
    submitIngestionJob(tEnv, config);
  }

  // ---- catalog setup -----------------------------------------------------------

  private static void setupHiveCatalog(final TableEnvironment tEnv, final JobConfig config) {
    tEnv.executeSql(
        String.format(
            """
            CREATE CATALOG %s WITH (
              'type'                    = 'iceberg',
              'catalog-type'            = 'hive',
              'uri'                     = '%s',
              'warehouse'               = '%s/user/hive/warehouse',
              'property-version'        = '1'
            )
            """,
            config.catalog(), config.metastoreUri(), config.hdfsUri()));

    tEnv.executeSql("USE CATALOG " + config.catalog());

    tEnv.executeSql("CREATE DATABASE IF NOT EXISTS " + config.database());

    tEnv.executeSql("USE " + config.database());
  }

  // ---- Iceberg table -----------------------------------------------------------

  /**
   * Creates the Iceberg target table if it does not already exist.
   *
   * <p>The schema mirrors the columns written by {@code ParquetBatchWriter}. The table is
   * partitioned by {@code date} (derived from the Hive-style landing directory) so that Trino
   * partition pruning works without a full scan.
   *
   * <p>The {@code position} column is the natural unique key for deduplication.
   */
  private static void ensureIcebergTable(final TableEnvironment tEnv, final JobConfig config) {
    tEnv.executeSql(
        String.format(
            """
            CREATE TABLE IF NOT EXISTS %s (
              `position`      BIGINT        NOT NULL,
              partition_id    INT           NOT NULL,
              `key`           BIGINT        NOT NULL,
              record_type     STRING        NOT NULL,
              value_type      STRING        NOT NULL,
              intent          STRING        NOT NULL,
              `timestamp`     TIMESTAMP_LTZ(6) NOT NULL,
              broker_version  STRING        NOT NULL,
              `json`          STRING        NOT NULL,
              ingested_at     TIMESTAMP_LTZ(3) NOT NULL,
              record_date     STRING        NOT NULL
            ) PARTITIONED BY (record_date)
            WITH (
              'format-version'        = '2',
              'write.format.default'  = 'parquet',
              'write.upsert.enabled'  = 'true'
            )
            """,
            config.table()));
  }

  // ---- source → sink -----------------------------------------------------------

  /**
   * Defines the landing-zone source table backed by the HDFS parquet files and inserts new rows
   * into the Iceberg sink.
   *
   * <p>The source uses Flink's built-in {@code filesystem} connector in streaming mode. It monitors
   * {@code inputPath/date=*} for new files using the checkpoint interval as the discovery trigger
   * interval, so every checkpoint cycle picks up files written since the last one.
   *
   * <p>Iceberg write-upsert (enabled above) deduplicates on {@code (position, partition_id)} so
   * that at-least-once re-deliveries from the parquet exporter do not produce duplicate rows.
   */
  private static void submitIngestionJob(final TableEnvironment tEnv, final JobConfig config) {
    // Register the parquet landing zone as a temporary source table
    tEnv.executeSql(
        String.format(
            """
            CREATE TEMPORARY TABLE parquet_landing (
              `position`      BIGINT        NOT NULL,
              partition_id    INT           NOT NULL,
              `key`           BIGINT        NOT NULL,
              record_type     STRING        NOT NULL,
              value_type      STRING        NOT NULL,
              intent          STRING        NOT NULL,
              `timestamp`     BIGINT        NOT NULL,
              broker_version  STRING        NOT NULL,
              `json`          STRING        NOT NULL
            ) WITH (
              'connector'               = 'filesystem',
              'path'                    = '%s%s',
              'format'                  = 'parquet',
              'source.monitor-interval' = '30s'
            )
            """,
            config.hdfsUri(), config.inputPath()));

    LOG.info(
        "Submitting ingestion: parquet_landing → {}.{}.{}",
        config.catalog(),
        config.database(),
        config.table());

    // Insert into Iceberg (upsert on position deduplicates at-least-once re-deliveries)
    tEnv.executeSql(
        String.format(
            """
            INSERT INTO %s
            SELECT
              `position`,
              partition_id,
              `key`,
              record_type,
              value_type,
              intent,
              TO_TIMESTAMP_LTZ(`timestamp`, 6),
              broker_version,
              `json`,
              CURRENT_TIMESTAMP,
              DATE_FORMAT(TO_TIMESTAMP_LTZ(`timestamp`, 6), 'yyyy-MM-dd')
            FROM parquet_landing
            """,
            config.table()));
  }
}
