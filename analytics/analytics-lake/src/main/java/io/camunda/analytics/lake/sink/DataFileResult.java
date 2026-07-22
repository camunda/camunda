/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink;

import org.apache.iceberg.Metrics;

/**
 * What an encoder hands back for one finished data file — everything the descriptor sink needs to
 * register it with Iceberg (via {@code DataFiles.builder}) without re-reading the file.
 *
 * @param table Iceberg table name
 * @param path absolute file location (as the catalog will store it)
 * @param rowCount rows written
 * @param fileSizeBytes final size
 * @param metrics per-column metrics collected during the write (iceberg-parquet provides these;
 *     never re-derive by re-reading)
 * @param epochDay the file's family day, or {@code -1} for the mixed-day spill file
 */
public record DataFileResult(
    String table, String path, long rowCount, long fileSizeBytes, Metrics metrics, long epochDay) {}
