/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.metrics;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer.Sample;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.Histogram.Timer;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class BackupManagerMetrics {
  private static final String NAMESPACE = "zeebe";
  private static final String LABEL_NAME_PARTITION = "partition";
  private static final String LABEL_NAME_OPERATION = "operation";
  private static final String LABEL_NAME_RESULT = "result";

  private static final String FAILED = "failed";
  private static final String COMPLETED = "completed";
  private static final String TAKE_OPERATION = "take";
  private static final String STATUS_OPERATION = "status";
  private static final String LIST_OPERATION = "list";
  private static final String DELETE_OPERATION = "delete";

  private static final io.micrometer.core.instrument.MeterRegistry METER_REGISTRY =
      Metrics.globalRegistry;

  private static final Counter TOTAL_OPERATIONS =
      Counter.build()
          .namespace(NAMESPACE)
          .name("backup_operations_total")
          .help("Total number of backup operations")
          .labelNames(LABEL_NAME_PARTITION, LABEL_NAME_OPERATION, LABEL_NAME_RESULT)
          .register();

  private static final io.micrometer.core.instrument.Counter.Builder TOTAL_OPERATIONS_BUILDER =
      io.micrometer.core.instrument.Counter.builder(NAMESPACE + "_backup_operations_total_micro")
          .description("Total number of backup operations");

  private static final Gauge OPERATIONS_IN_PROGRESS =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("backup_operations_in_progress")
          .help("Number of backup operations that are in progress")
          .labelNames(LABEL_NAME_PARTITION, LABEL_NAME_OPERATION)
          .register();

  private static final ConcurrentHashMap<String, AtomicLong>
      OPERATIONS_IN_PROGRESS_MICROMETER_GAUGES = new ConcurrentHashMap<>();

  private static final Histogram BACKUP_OPERATION_LATENCY =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("backup_operations_latency")
          .help("Latency of backup operations")
          .labelNames(LABEL_NAME_PARTITION, LABEL_NAME_OPERATION)
          .buckets(0.01, 0.1, 1, 10, 60, 5 * 60)
          .register();

  private static final Duration[] BACKUP_OPERATION_LATENCY_BUCKETS =
      Stream.of(10, 100, 1000, 10000, 60000, 5 * 60000)
          .map(Duration::ofMillis) // TODO Verify unit
          .toArray(Duration[]::new);

  private static final io.micrometer.core.instrument.Timer BACKUP_OPERATION_LATENCY_MICROMETER =
      io.micrometer.core.instrument.Timer.builder(NAMESPACE + "_backup_operations_latency_micro")
          .description("Latency of backup operations")
          .sla(BACKUP_OPERATION_LATENCY_BUCKETS)
          .register(METER_REGISTRY);

  private final String partitionId;

  public BackupManagerMetrics(final int partitionId) {
    this.partitionId = String.valueOf(partitionId);
  }

  public OperationMetrics startTakingBackup() {
    return OperationMetrics.start(partitionId, TAKE_OPERATION);
  }

  public OperationMetrics startQueryingStatus() {
    return OperationMetrics.start(partitionId, STATUS_OPERATION);
  }

  public OperationMetrics startListingBackups() {
    return OperationMetrics.start(partitionId, LIST_OPERATION);
  }

  public OperationMetrics startDeleting() {
    return OperationMetrics.start(partitionId, DELETE_OPERATION);
  }

  public void cancelInProgressOperations() {
    OPERATIONS_IN_PROGRESS.labels(partitionId, TAKE_OPERATION).set(0);
    OPERATIONS_IN_PROGRESS.labels(partitionId, DELETE_OPERATION).set(0);
    OPERATIONS_IN_PROGRESS.labels(partitionId, STATUS_OPERATION).set(0);
    OPERATIONS_IN_PROGRESS.labels(partitionId, LIST_OPERATION).set(0);
    getOperationsInProgressGauge(partitionId, TAKE_OPERATION).set(0);
    getOperationsInProgressGauge(partitionId, DELETE_OPERATION).set(0);
    getOperationsInProgressGauge(partitionId, STATUS_OPERATION).set(0);
    getOperationsInProgressGauge(partitionId, LIST_OPERATION).set(0);
  }

  private static AtomicLong getOperationsInProgressGauge(
      final String partitionId, final String operation) {
    final String key = operation + "_" + partitionId;
    return OPERATIONS_IN_PROGRESS_MICROMETER_GAUGES.computeIfAbsent(
        key,
        k -> {
          final AtomicLong newGaugeValue = new AtomicLong(0);
          io.micrometer.core.instrument.Gauge.builder(
                  NAMESPACE + "_backup_operations_in_progress_micro",
                  newGaugeValue,
                  AtomicLong::get)
              .description("Number of backup operations that are in progress")
              .tag(LABEL_NAME_PARTITION, partitionId)
              .tag(LABEL_NAME_OPERATION, operation)
              .register(METER_REGISTRY);
          return newGaugeValue;
        });
  }

  public static final class OperationMetrics {
    final String partitionId;
    final Histogram.Timer timer;
    final Sample timerSample;
    final String operation;

    private OperationMetrics(
        final String partitionId,
        final Timer timer,
        final Sample timerSample,
        final String operation) {
      this.partitionId = partitionId;
      this.timer = timer;
      this.timerSample = timerSample;
      this.operation = operation;
    }

    private static OperationMetrics start(final String partitionId, final String operation) {
      final var timer = BACKUP_OPERATION_LATENCY.labels(partitionId, operation).startTimer();
      final io.micrometer.core.instrument.Timer.Sample timerSample =
          io.micrometer.core.instrument.Timer.start(METER_REGISTRY);
      OPERATIONS_IN_PROGRESS.labels(partitionId, operation).inc();
      getOperationsInProgressGauge(partitionId, operation).incrementAndGet();
      return new OperationMetrics(partitionId, timer, timerSample, operation);
    }

    public <T> void complete(final T ignored, final Throwable throwable) {
      timer.close();
      if (timerSample != null) {
        final var microTimer =
            METER_REGISTRY.timer(
                NAMESPACE + "_backup_operations_latency_micro",
                LABEL_NAME_PARTITION,
                partitionId,
                LABEL_NAME_OPERATION,
                operation);
        if (microTimer != null) {
          timerSample.stop(microTimer);
        }
      }

      OPERATIONS_IN_PROGRESS.labels(partitionId, operation).dec();
      getOperationsInProgressGauge(partitionId, operation).decrementAndGet();
      if (throwable != null) {
        TOTAL_OPERATIONS_BUILDER
            .tags(
                LABEL_NAME_PARTITION,
                partitionId,
                LABEL_NAME_OPERATION,
                operation,
                LABEL_NAME_RESULT,
                FAILED)
            .register(METER_REGISTRY)
            .increment();
        TOTAL_OPERATIONS.labels(partitionId, operation, FAILED).inc();
      } else {
        TOTAL_OPERATIONS_BUILDER
            .tags(
                LABEL_NAME_PARTITION,
                partitionId,
                LABEL_NAME_OPERATION,
                operation,
                LABEL_NAME_RESULT,
                COMPLETED)
            .register(METER_REGISTRY)
            .increment();
        TOTAL_OPERATIONS.labels(partitionId, operation, COMPLETED).inc();
      }
    }
  }
}
