/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
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

  private static final String BACKUP_OPERATIONS_TIMER_NAME =
      NAMESPACE + "_backup_operations_latency";
  private static final String BACKUP_OPERATIONS_IN_PROGRESS_NAME =
      NAMESPACE + "_backup_operations_in_progress";
  private static final MeterRegistry METER_REGISTRY = Metrics.globalRegistry;
  private static final Counter.Builder TOTAL_OPERATIONS_BUILDER =
      Counter.builder(NAMESPACE + "_backup_operations_total")
          .description("Total number of backup operations");
  private static final ConcurrentHashMap<String, AtomicLong> OPERATIONS_IN_PROGRESS_GAUGES =
      new ConcurrentHashMap<>();
  private static final Duration[] BACKUP_OPERATION_LATENCY_BUCKETS =
      Stream.of(10, 100, 1000, 10000, 60000, 5 * 60000)
          .map(Duration::ofMillis)
          .toArray(Duration[]::new);

  static {
    Timer.builder(BACKUP_OPERATIONS_TIMER_NAME)
        .description("Latency of backup operations")
        .sla(BACKUP_OPERATION_LATENCY_BUCKETS)
        .register(METER_REGISTRY);
  }

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
    getOperationsInProgressGauge(partitionId, TAKE_OPERATION).set(0);
    getOperationsInProgressGauge(partitionId, DELETE_OPERATION).set(0);
    getOperationsInProgressGauge(partitionId, STATUS_OPERATION).set(0);
    getOperationsInProgressGauge(partitionId, LIST_OPERATION).set(0);
  }

  private static AtomicLong getOperationsInProgressGauge(
      final String partitionId, final String operation) {
    final String key = operation + "_" + partitionId;
    return OPERATIONS_IN_PROGRESS_GAUGES.computeIfAbsent(
        key,
        k -> {
          final AtomicLong newGaugeValue = new AtomicLong(0);
          Gauge.builder(BACKUP_OPERATIONS_IN_PROGRESS_NAME, newGaugeValue, AtomicLong::get)
              .description("Number of backup operations that are in progress")
              .tag(LABEL_NAME_PARTITION, partitionId)
              .tag(LABEL_NAME_OPERATION, operation)
              .register(METER_REGISTRY);
          return newGaugeValue;
        });
  }

  public static final class OperationMetrics {
    final String partitionId;
    final Sample timerSample;
    final String operation;

    private OperationMetrics(
        final String partitionId, final Sample timerSample, final String operation) {
      this.partitionId = partitionId;
      this.timerSample = timerSample;
      this.operation = operation;
    }

    private static OperationMetrics start(final String partitionId, final String operation) {
      final Timer.Sample timerSample = Timer.start(METER_REGISTRY);
      getOperationsInProgressGauge(partitionId, operation).incrementAndGet();
      return new OperationMetrics(partitionId, timerSample, operation);
    }

    public <T> void complete(final T ignored, final Throwable throwable) {
      if (timerSample != null) {
        final var timer =
            METER_REGISTRY.timer(
                BACKUP_OPERATIONS_TIMER_NAME,
                LABEL_NAME_PARTITION,
                partitionId,
                LABEL_NAME_OPERATION,
                operation);
        timerSample.stop(timer);
      }

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
      }
    }
  }
}
