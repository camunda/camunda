/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import io.camunda.zeebe.db.ColumnFamilyMetrics;
import io.camunda.zeebe.protocol.EnumValue;
import io.prometheus.client.Histogram;
import io.prometheus.client.Histogram.Child;
import io.prometheus.client.Histogram.Timer;

public final class FineGrainedColumnFamilyMetrics implements ColumnFamilyMetrics {

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

  public <ColumnFamilyNames extends Enum<? extends EnumValue> & EnumValue>
      FineGrainedColumnFamilyMetrics(final int partitionId, final ColumnFamilyNames columnFamily) {
    final var partitionLabel = String.valueOf(partitionId);
    final var columnFamilyLabel = columnFamily.name();
    getLatency = LATENCY.labels(partitionLabel, columnFamilyLabel, "get");
    putLatency = LATENCY.labels(partitionLabel, columnFamilyLabel, "put");
    deleteLatency = LATENCY.labels(partitionLabel, columnFamilyLabel, "delete");
    iterateLatency = LATENCY.labels(partitionLabel, columnFamilyLabel, "iterate");
  }

  @Override
  public Timer measureGetLatency() {
    return getLatency.startTimer();
  }

  @Override
  public Timer measurePutLatency() {
    return putLatency.startTimer();
  }

  @Override
  public Timer measureDeleteLatency() {
    return deleteLatency.startTimer();
  }

  @Override
  public Timer measureIterateLatency() {
    return iterateLatency.startTimer();
  }
}
