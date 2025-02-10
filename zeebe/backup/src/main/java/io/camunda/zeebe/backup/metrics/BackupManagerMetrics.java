/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.metrics;

import static io.camunda.zeebe.backup.metrics.BackupManagerMetricsDoc.*;

import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.collection.Table;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.EnumMap;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public class BackupManagerMetrics {

  private final MeterRegistry registry;
  private final Table<OperationType, OperationResult, Counter> totalOperations = Table.simple();
  private final EnumMap<OperationType, AtomicLong> operationInProgress =
      new EnumMap<>(OperationType.class);
  private final EnumMap<OperationType, Timer> backupOperationLatency =
      new EnumMap<>(OperationType.class);

  public BackupManagerMetrics(final MeterRegistry meterRegistry) {
    registry = Objects.requireNonNull(meterRegistry, "meterRegistry cannot be null");
  }

  public OperationMetrics startTakingBackup() {
    return start(OperationType.TAKE);
  }

  public OperationMetrics startQueryingStatus() {
    return start(OperationType.STATUS);
  }

  public OperationMetrics startListingBackups() {
    return start(OperationType.LIST);
  }

  public OperationMetrics startDeleting() {
    return start(OperationType.DELETE);
  }

  public void cancelInProgressOperations() {
    for (final var operation : OperationType.values()) {
      final var value = operationInProgress.get(operation);
      if (value != null) {
        value.set(0L);
      }
    }
  }

  private Counter registerTotalOperation(
      final OperationType operation, final OperationResult result) {
    return Counter.builder(BACKUP_OPERATIONS_TOTAL.getName())
        .description(BACKUP_OPERATIONS_TOTAL.getDescription())
        .tags(
            MetricKeyName.OPERATION.asString(),
            operation.getValue(),
            MetricKeyName.RESULT.asString(),
            result.getValue())
        .register(registry);
  }

  private Timer registerBackupLatency(final OperationType operationType) {
    return Timer.builder(BACKUP_OPERATIONS_LATENCY.getName())
        .description(BACKUP_OPERATIONS_LATENCY.getDescription())
        .tags(MetricKeyName.OPERATION.asString(), operationType.name())
        .register(registry);
  }

  private AtomicLong getOperationInProgress(final OperationType operationType) {
    var value = operationInProgress.get(operationType);
    if (value == null) {
      value = new AtomicLong();
      operationInProgress.put(operationType, value);
      Gauge.builder(BACKUP_OPERATIONS_IN_PROGRESS.getName(), value::get)
          .description(BACKUP_OPERATIONS_IN_PROGRESS.getDescription())
          .register(registry);
    }
    return value;
  }

  private OperationMetrics start(final OperationType operation) {
    final var timer =
        backupOperationLatency.computeIfAbsent(operation, this::registerBackupLatency);
    final var timerSample = MicrometerUtil.timer(timer, Timer.start(registry.config().clock()));
    getOperationInProgress(operation).incrementAndGet();
    return new OperationMetrics(timerSample, operation);
  }

  public final class OperationMetrics {
    final CloseableSilently timer;
    final OperationType operation;

    private OperationMetrics(final CloseableSilently timer, final OperationType operation) {
      this.timer = timer;
      this.operation = operation;
    }

    public <T> void complete(final T ignored, final Throwable throwable) {
      timer.close();
      getOperationInProgress(operation).decrementAndGet();
      final var result = throwable != null ? OperationResult.FAILED : OperationResult.COMPLETED;
      totalOperations
          .computeIfAbsent(operation, result, BackupManagerMetrics.this::registerTotalOperation)
          .increment();
    }
  }
}
