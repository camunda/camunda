/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.metrics;

import io.micrometer.core.instrument.Metrics;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class CheckpointMetrics {

  private static final String NAMESPACE = "zeebe";
  private static final String LABEL_NAME_PARTITION = "partition";
  private static final String LABEL_NAME_RESULT = "result";

  private static final io.micrometer.core.instrument.MeterRegistry METER_REGISTRY =
      Metrics.globalRegistry;

  private static final io.micrometer.core.instrument.Counter.Builder CHECKPOINT_RECORDS_BUILDER =
      io.micrometer.core.instrument.Counter.builder(NAMESPACE + "_checkpoint_records_total_micro")
          .description(
              "Number of checkpoint records processed by stream processor. Processing can result in either creating a new checkpoint or ignoring the record. This can be observed by filtering for label 'result'.");

  private static final Counter CHECKPOINT_RECORDS =
      Counter.build()
          .namespace(NAMESPACE)
          .name("checkpoint_records_total")
          .help(
              "Number of checkpoint records processed by stream processor. Processing can result in either creating a new checkpoint or ignoring the record. This can be observed by filtering for label 'result'.")
          .labelNames(LABEL_NAME_RESULT, LABEL_NAME_PARTITION)
          .register();

  private static final ConcurrentHashMap<String, AtomicLong> GAUGES = new ConcurrentHashMap<>();

  private static final String CHECKPOINT_POSITION_NAME = NAMESPACE + "_checkpoint_position_micro";
  private static final String CHECKPOINT_ID_NAME = NAMESPACE + "_checkpoint_id_micro";

  private static final Gauge CHECKPOINT_POSITION =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("checkpoint_position")
          .help("Position of the last checkpoint")
          .labelNames(LABEL_NAME_PARTITION)
          .register();

  private static final Gauge CHECKPOINT_ID =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("checkpoint_id")
          .help("Id of the last checkpoint")
          .labelNames(LABEL_NAME_PARTITION)
          .register();

  final String partitionId;

  public CheckpointMetrics(final int partitionId) {
    this.partitionId = String.valueOf(partitionId);
  }

  public void created(final long checkpointId, final long checkpointPosition) {
    setCheckpointId(checkpointId, checkpointPosition);
    CHECKPOINT_RECORDS.labels("created", partitionId).inc();
    CHECKPOINT_RECORDS_BUILDER
        .tags(LABEL_NAME_RESULT, "created", LABEL_NAME_PARTITION, partitionId)
        .register(METER_REGISTRY)
        .increment();
  }

  public void setCheckpointId(final long checkpointId, final long checkpointPosition) {
    CHECKPOINT_ID.labels(partitionId).set(checkpointId);
    final String idKey = CHECKPOINT_ID_NAME + "_" + partitionId;
    final AtomicLong newCheckpointId =
        GAUGES.computeIfAbsent(
            idKey,
            k -> {
              final AtomicLong newGaugeValue = new AtomicLong(0);
              io.micrometer.core.instrument.Gauge.builder(
                      CHECKPOINT_ID_NAME, newGaugeValue, AtomicLong::get)
                  .description("Id of the last checkpoint")
                  .tag(LABEL_NAME_PARTITION, partitionId)
                  .register(METER_REGISTRY);
              return newGaugeValue;
            });
    newCheckpointId.set(checkpointId);

    CHECKPOINT_POSITION.labels(partitionId).set(checkpointPosition);
    final String positionKey = CHECKPOINT_POSITION_NAME + "_" + partitionId;
    final AtomicLong newCheckpointPosition =
        GAUGES.computeIfAbsent(
            positionKey,
            k -> {
              final AtomicLong newGaugeValue = new AtomicLong(0);
              io.micrometer.core.instrument.Gauge.builder(
                      CHECKPOINT_POSITION_NAME, newGaugeValue, AtomicLong::get)
                  .description("Position of the last checkpoint")
                  .tag(LABEL_NAME_PARTITION, partitionId)
                  .register(METER_REGISTRY);
              return newGaugeValue;
            });
    newCheckpointPosition.set(checkpointPosition);
  }

  public void ignored() {
    CHECKPOINT_RECORDS.labels("ignored", partitionId).inc();
    CHECKPOINT_RECORDS_BUILDER
        .tags(LABEL_NAME_RESULT, "ignored", LABEL_NAME_PARTITION, partitionId)
        .register(METER_REGISTRY)
        .increment();
  }
}
