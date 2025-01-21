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
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.prometheus.client.Enumeration;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.Histogram.Timer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
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

  private static final Gauge TOPOLOGY_VERSION =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("cluster_topology_version")
          .help("The version of the cluster topology")
          .register();

  private static final Gauge CHANGE_ID =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("cluster_changes_id")
          .help("The id of the cluster topology change plan")
          .register();

  private static final Enumeration CHANGE_STATUS =
      Enumeration.build()
          .namespace(NAMESPACE)
          .name("cluster_changes_status")
          .help("The state of the current cluster topology")
          .states(ClusterChangePlan.Status.class)
          .register();
  private static final Gauge CHANGE_VERSION =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("cluster_changes_version")
          .help("The version of the cluster topology change plan")
          .register();

  private static final Gauge PENDING_OPERATIONS =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("cluster_changes_operations_pending")
          .help("Number of pending changes in the current change plan")
          .register();
  private static final Gauge COMPLETED_OPERATIONS =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("cluster_changes_operations_completed")
          .help("Number of completed changes in the current change plan")
          .register();
  private static final Histogram OPERATION_DURATION =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("cluster_changes_operation_duration")
          .help("Duration it takes to apply an operation")
          .labelNames(LABEL_OPERATION)
          .buckets(0.1, 1, 2, 5, 10, 30, 60, 120, 180, 300, 600)
          .register();

  private static final ConcurrentHashMap<String, io.micrometer.core.instrument.Counter>
      operationAttempts = new ConcurrentHashMap<>();

  private static final io.micrometer.core.instrument.MeterRegistry meterRegistry =
      Metrics.globalRegistry;

  private static final AtomicLong MICRO_CHANGE_ID = new AtomicLong(0);
  private static final AtomicInteger MICRO_CHANGE_STATUS = new AtomicInteger(0);
  private static final AtomicInteger MICRO_CHANGE_VERSION = new AtomicInteger(0);
  private static final AtomicLong MICRO_TOPOLOGY_VERSION = new AtomicLong(0);
  private static final AtomicInteger MICRO_PENDING_OPERATIONS = new AtomicInteger(0);
  private static final AtomicInteger MICRO_COMPLETED_OPERATIONS = new AtomicInteger(0);

  static {
    io.micrometer.core.instrument.Gauge.builder(
        NAMESPACE + "_cluster_topology_version_micro", MICRO_TOPOLOGY_VERSION, AtomicLong::get);
    io.micrometer.core.instrument.Gauge.builder(
            NAMESPACE + "_cluster_changes_operations_pending_micro",
            MICRO_PENDING_OPERATIONS,
            AtomicInteger::get)
        .description("Number of pending changes in the current change plan")
        .register(meterRegistry);

    io.micrometer.core.instrument.Gauge.builder(
            NAMESPACE + "_cluster_changes_operations_completed_micro",
            MICRO_COMPLETED_OPERATIONS,
            AtomicInteger::get)
        .description("Number of completed changes in the current change plan")
        .register(meterRegistry);

    io.micrometer.core.instrument.Gauge.builder(
            NAMESPACE + "_cluster_changes_version_micro", MICRO_CHANGE_VERSION, AtomicInteger::get)
        .description("The version of the cluster topology change plan")
        .register(meterRegistry);

    io.micrometer.core.instrument.Gauge.builder(
            NAMESPACE + "_cluster_changes_id_micro", MICRO_CHANGE_ID, AtomicLong::get)
        .description("The id of the cluster topology change plan")
        .register(meterRegistry);

    io.micrometer.core.instrument.Gauge.builder(
            NAMESPACE + "_cluster_changes_status_micro", MICRO_CHANGE_STATUS, AtomicInteger::get)
        .description("The state of the current cluster topology")
        .register(meterRegistry);
  }

  public static void updateFromTopology(final ClusterConfiguration topology) {
    MICRO_TOPOLOGY_VERSION.set(topology.version());
    TOPOLOGY_VERSION.set(topology.version());

    MICRO_CHANGE_STATUS.set(
        topology
            .pendingChanges()
            .map(ClusterChangePlan::status)
            .or(() -> topology.lastChange().map(CompletedChange::status))
            .orElse(Status.COMPLETED)
            .ordinal());

    CHANGE_STATUS.state(
        topology
            .pendingChanges()
            .map(ClusterChangePlan::status)
            .or(() -> topology.lastChange().map(CompletedChange::status))
            .orElse(Status.COMPLETED));

    MICRO_CHANGE_ID.set(topology.pendingChanges().map(ClusterChangePlan::id).orElse(0L));
    CHANGE_ID.set(topology.pendingChanges().map(ClusterChangePlan::id).orElse(0L));

    MICRO_CHANGE_VERSION.set(topology.pendingChanges().map(ClusterChangePlan::version).orElse(0));
    CHANGE_VERSION.set(topology.pendingChanges().map(ClusterChangePlan::version).orElse(0));

    MICRO_PENDING_OPERATIONS.set(
        topology
            .pendingChanges()
            .map(ClusterChangePlan::pendingOperations)
            .map(List::size)
            .orElse(0));
    PENDING_OPERATIONS.set(
        topology
            .pendingChanges()
            .map(ClusterChangePlan::pendingOperations)
            .map(List::size)
            .orElse(0));

    MICRO_COMPLETED_OPERATIONS.set(
        topology
            .pendingChanges()
            .map(ClusterChangePlan::completedOperations)
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
              .register(meterRegistry);
    }

    static OperationObserver startOperation(final ClusterConfigurationChangeOperation operation) {
      return new OperationObserver(
          operation,
          OPERATION_DURATION.labels(operation.getClass().getSimpleName()).startTimer(),
          io.micrometer.core.instrument.Timer.start(meterRegistry));
    }

    protected io.micrometer.core.instrument.Counter newCounter(
        final String metricName, final String operation, final String outcome) {
      final List<Tag> tags = new ArrayList<>();
      if (operation != null && !operation.isEmpty()) {
        tags.add(Tag.of(LABEL_OPERATION, operation));
      }
      if (outcome != null && !outcome.isEmpty()) {
        tags.add(Tag.of(LABEL_OUTCOME, outcome));
      }
      return meterRegistry.counter(metricName, tags);
    }

    public void failed() {
      if (clusterChangeOperationTimer != null && clusterChangeOperationTimerSample != null) {
        clusterChangeOperationTimerSample.stop(clusterChangeOperationTimer);
      }
      if (timer != null) {
        timer.close();
        final String key =
            NAMESPACE
                + "_cluster_changes_operation_attempts#"
                + operation.getClass().getSimpleName()
                + "#failed";
        final io.micrometer.core.instrument.Counter counter =
            operationAttempts.computeIfAbsent(
                key,
                k ->
                    newCounter(
                        NAMESPACE + "_cluster_changes_operation_attempts",
                        operation.getClass().getSimpleName(),
                        "failed"));
        counter.increment();
      }
    }

    public void applied() {
      if (clusterChangeOperationTimer != null && clusterChangeOperationTimerSample != null) {
        clusterChangeOperationTimerSample.stop(clusterChangeOperationTimer);
      }
      if (timer != null) {
        timer.close();
        final String key =
            NAMESPACE
                + "_cluster_changes_operation_attempts#"
                + operation.getClass().getSimpleName()
                + "#applied";
        final io.micrometer.core.instrument.Counter counter =
            operationAttempts.computeIfAbsent(
                key,
                k ->
                    newCounter(
                        NAMESPACE + "_cluster_changes_operation_attempts",
                        operation.getClass().getSimpleName(),
                        "applied"));
        counter.increment();
      }
    }
  }
}
