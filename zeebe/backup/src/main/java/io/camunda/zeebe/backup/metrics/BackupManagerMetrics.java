/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.metrics;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.Histogram.Timer;

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

  private static final Counter TOTAL_OPERATIONS =
      Counter.build()
          .namespace(NAMESPACE)
          .name("backup_operations_total")
          .help("Total number of backup operations")
          .labelNames(LABEL_NAME_PARTITION, LABEL_NAME_OPERATION, LABEL_NAME_RESULT)
          .register();
  private static final Gauge OPERATIONS_IN_PROGRESS =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("backup_operations_in_progress")
          .help("Number of backup operations that are in progress")
          .labelNames(LABEL_NAME_PARTITION, LABEL_NAME_OPERATION)
          .register();

  private static final Histogram BACKUP_OPERATION_LATENCY =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("backup_operations_latency")
          .help("Latency of backup operations")
          .labelNames(LABEL_NAME_PARTITION, LABEL_NAME_OPERATION)
          .buckets(0.01, 0.1, 1, 10, 60, 5 * 60)
          .register();

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
  }

  public static final class OperationMetrics {
    final String partitionId;
    final Histogram.Timer timer;
    final String operation;

    private OperationMetrics(final String partitionId, final Timer timer, final String operation) {
      this.partitionId = partitionId;
      this.timer = timer;
      this.operation = operation;
    }

    private static OperationMetrics start(final String partitionId, final String operation) {
      final var timer = BACKUP_OPERATION_LATENCY.labels(partitionId, operation).startTimer();
      OPERATIONS_IN_PROGRESS.labels(partitionId, operation).inc();
      return new OperationMetrics(partitionId, timer, operation);
    }

    public <T> void complete(final T ignored, final Throwable throwable) {
      timer.close();
      OPERATIONS_IN_PROGRESS.labels(partitionId, operation).dec();
      if (throwable != null) {
        TOTAL_OPERATIONS.labels(partitionId, operation, FAILED).inc();
      } else {
        TOTAL_OPERATIONS.labels(partitionId, operation, COMPLETED).inc();
      }
    }
  }
}
