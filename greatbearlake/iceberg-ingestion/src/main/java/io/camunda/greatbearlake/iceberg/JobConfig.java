/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.greatbearlake.iceberg;

import java.util.HashMap;
import java.util.Map;

/**
 * Parsed command-line configuration for {@link IcebergIngestionJob}.
 *
 * <p>Arguments are passed as {@code --key value} pairs. Unknown keys are ignored.
 */
record JobConfig(
    String hdfsUri,
    String inputPath,
    String metastoreUri,
    String catalog,
    String database,
    String table,
    long checkpointMs) {

  static JobConfig parse(final String[] args) {
    final Map<String, String> map = new HashMap<>();
    for (int i = 0; i < args.length - 1; i += 2) {
      if (args[i].startsWith("--")) {
        map.put(args[i].substring(2), args[i + 1]);
      }
    }
    return new JobConfig(
        map.getOrDefault("hdfsUri", "hdfs://namenode:9000"),
        map.getOrDefault("inputPath", "/zeebe/export"),
        map.getOrDefault("metastoreUri", "thrift://hive-metastore:9083"),
        map.getOrDefault("catalog", "zeebe_iceberg"),
        map.getOrDefault("database", "zeebe"),
        map.getOrDefault("table", "zeebe_records"),
        Long.parseLong(map.getOrDefault("checkpointMs", "60000")));
  }
}
