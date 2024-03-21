/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl;

import io.camunda.zeebe.protocol.EnumValue;
import io.prometheus.client.Histogram;
import io.prometheus.client.Histogram.Child;
import io.prometheus.client.Histogram.Timer;

public final class ColumnFamilyMetrics {

  private static final Histogram LATENCY =
      Histogram.build()
          .namespace("zeebe")
          .name("rocksdb_latency")
          .exponentialBuckets(0.00001, 2, 15)
          .labelNames("partition", "columnFamily", "operation")
          .help("Latency of RocksDB operations per column family")
          .register();

  private final Child getLatency;
  private final Child putLatency;
  private final Child deleteLatency;
  private final Child iterateLatency;
  private final boolean enabled;

  public <ColumnFamilyNames extends Enum<? extends EnumValue> & EnumValue> ColumnFamilyMetrics(
      final boolean enabled, final int partitionId, final ColumnFamilyNames columnFamily) {
    this.enabled = enabled;
    final var partitionLabel = String.valueOf(partitionId);
    final var columnFamilyLabel = columnFamily.name();
    getLatency = LATENCY.labels(partitionLabel, columnFamilyLabel, "get");
    putLatency = LATENCY.labels(partitionLabel, columnFamilyLabel, "put");
    deleteLatency = LATENCY.labels(partitionLabel, columnFamilyLabel, "delete");
    iterateLatency = LATENCY.labels(partitionLabel, columnFamilyLabel, "iterate");
  }

  public Timer measureGetLatency() {
    return enabled ? getLatency.startTimer() : null;
  }

  public Timer measurePutLatency() {
    return enabled ? putLatency.startTimer() : null;
  }

  public Timer measureDeleteLatency() {
    return enabled ? deleteLatency.startTimer() : null;
  }

  public Timer measureIterateLatency() {
    return enabled ? iterateLatency.startTimer() : null;
  }
}
