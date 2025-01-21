/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.metrics;

import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.Status;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CompletedChange;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.prometheus.client.Enumeration;
import io.prometheus.client.Histogram;
import io.prometheus.client.Histogram.Timer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public final class TopologyMetrics {
  private static final String NAMESPACE = "zeebe";
  private static final String LABEL_OPERATION = "operation";
  private static final String LABEL_OUTCOME = "outcome";

  private static final Duration[] OPERATION_DURATION_BUCKETS =
      Stream.of(100, 1000, 2000, 5000, 10000, 30000, 60000, 120000, 180000, 300000, 600000)
          .map(Duration::ofMillis)
          .toArray(Duration[]::new);

  private static final Enumeration CHANGE_STATUS =
      Enumeration.build()
          .namespace(NAMESPACE)
          .name("cluster_changes_status")
          .help("The state of the current cluster topology")
          .states(ClusterChangePlan.Status.class)
          .register();

  private static final Histogram OPERATION_DURATION =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("cluster_changes_operation_duration")
          .help("Duration it takes to apply an operation")
          .labelNames(LABEL_OPERATION)
          .buckets(0.1, 1, 2, 5, 10, 30, 60, 120, 180, 300, 600)
          .register();

  private static final io.micrometer.core.instrument.MeterRegistry METER_REGISTRY =
      Metrics.globalRegistry;

  private static final AtomicLong CHANGE_ID = new AtomicLong(0);
  //  private static ClusterChangePlan.Status MICRO_CHANGE_STATUS = null;
  private static final AtomicInteger CHANGE_VERSION = new AtomicInteger(0);
  private static final AtomicLong TOPOLOGY_VERSION = new AtomicLong(0);
  private static final AtomicInteger PENDING_OPERATIONS = new AtomicInteger(0);
  private static final AtomicInteger COMPLETED_OPERATIONS = new AtomicInteger(0);

  static {
    io.micrometer.core.instrument.Gauge.builder(
            NAMESPACE + "_cluster_topology_version", TOPOLOGY_VERSION, AtomicLong::get)
        .description("The version of the cluster topology")
        .register(METER_REGISTRY);
    io.micrometer.core.instrument.Gauge.builder(
            NAMESPACE + "_cluster_changes_operations_pending",
            PENDING_OPERATIONS,
            AtomicInteger::get)
        .description("Number of pending changes in the current change plan")
        .register(METER_REGISTRY);

    io.micrometer.core.instrument.Gauge.builder(
            NAMESPACE + "_cluster_changes_operations_completed",
            COMPLETED_OPERATIONS,
            AtomicInteger::get)
        .description("Number of completed changes in the current change plan")
        .register(METER_REGISTRY);

    io.micrometer.core.instrument.Gauge.builder(
            NAMESPACE + "_cluster_changes_version", CHANGE_VERSION, AtomicInteger::get)
        .description("The version of the cluster topology change plan")
        .register(METER_REGISTRY);

    io.micrometer.core.instrument.Gauge.builder(
            NAMESPACE + "_cluster_changes_id", CHANGE_ID, AtomicLong::get)
        .description("The id of the cluster topology change plan")
        .register(METER_REGISTRY);

    //    io.micrometer.core.instrument.Gauge.builder(
    //            NAMESPACE + "_cluster_changes_status_micro",
    //            MICRO_CHANGE_STATUS,
    //            ClusterChangePlan.Status::ordinal)
    //        .description("The state of the current cluster topology")
    //        .register(meterRegistry);
  }

  public static void updateFromTopology(final ClusterConfiguration topology) {
    TOPOLOGY_VERSION.set(topology.version());

    //    MICRO_CHANGE_STATUS =
    //        topology
    //            .pendingChanges()
    //            .map(ClusterChangePlan::status)
    //            .or(() -> topology.lastChange().map(CompletedChange::status))
    //            .orElse(Status.COMPLETED);

    CHANGE_STATUS.state(
        topology
            .pendingChanges()
            .map(ClusterChangePlan::status)
            .or(() -> topology.lastChange().map(CompletedChange::status))
            .orElse(Status.COMPLETED));

    CHANGE_ID.set(topology.pendingChanges().map(ClusterChangePlan::id).orElse(0L));

    CHANGE_VERSION.set(topology.pendingChanges().map(ClusterChangePlan::version).orElse(0));

    PENDING_OPERATIONS.set(
        topology
            .pendingChanges()
            .map(ClusterChangePlan::pendingOperations)
            .map(List::size)
            .orElse(0));

    COMPLETED_OPERATIONS.set(
        topology
            .pendingChanges()
            .map(ClusterChangePlan::completedOperations)
            .map(List::size)
            .orElse(0));
  }

  public static OperationObserver observeOperation(
      final ClusterConfigurationChangeOperation operation) {
    return OperationObserver.startOperation(operation);
  }

  public static final class OperationObserver {
    final Counter.Builder counterBuilder =
        Counter.builder(NAMESPACE + "_cluster_changes_operation_attempts")
            .description("Number of attempts to apply an operation");
    private final ClusterConfigurationChangeOperation operation;
    private final Timer timer;
    private final io.micrometer.core.instrument.Timer.Sample clusterChangeOperationTimerSample;
    private final io.micrometer.core.instrument.Timer clusterChangeOperationTimer;

    private OperationObserver(
        final ClusterConfigurationChangeOperation operation,
        final Timer timer,
        final io.micrometer.core.instrument.Timer.Sample clusterChangeOperationTimerSample) {
      this.operation = operation;
      this.timer = timer;
      this.clusterChangeOperationTimerSample = clusterChangeOperationTimerSample;
      clusterChangeOperationTimer =
          io.micrometer.core.instrument.Timer.builder(
                  NAMESPACE + "_cluster_changes_operation_duration_micro")
              .description("Duration it takes to apply an operation")
              .tags(LABEL_OPERATION, operation.getClass().getSimpleName())
              .sla(OPERATION_DURATION_BUCKETS)
              .register(METER_REGISTRY);
    }

    static OperationObserver startOperation(final ClusterConfigurationChangeOperation operation) {
      return new OperationObserver(
          operation,
          OPERATION_DURATION.labels(operation.getClass().getSimpleName()).startTimer(),
          io.micrometer.core.instrument.Timer.start(METER_REGISTRY));
    }

    public void failed() {
      if (clusterChangeOperationTimer != null && clusterChangeOperationTimerSample != null) {
        clusterChangeOperationTimerSample.stop(clusterChangeOperationTimer);
      }
      if (timer != null) {
        timer.close();
        final Counter c =
            counterBuilder
                .tags(LABEL_OPERATION, operation.getClass().getSimpleName())
                .tags(LABEL_OUTCOME, "failed")
                .register(METER_REGISTRY);
        c.increment();
      }
    }

    public void applied() {
      if (clusterChangeOperationTimer != null && clusterChangeOperationTimerSample != null) {
        clusterChangeOperationTimerSample.stop(clusterChangeOperationTimer);
      }
      if (timer != null) {
        timer.close();
        final Counter c =
            counterBuilder
                .tags(LABEL_OPERATION, operation.getClass().getSimpleName())
                .tags(LABEL_OUTCOME, "applied")
                .register(METER_REGISTRY);
        c.increment();
      }
    }
  }
}
