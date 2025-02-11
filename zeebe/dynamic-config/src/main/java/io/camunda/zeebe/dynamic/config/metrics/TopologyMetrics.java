/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.metrics;

import static io.camunda.zeebe.dynamic.config.metrics.TopologyMetricsDoc.*;

import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.Status;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CompletedChange;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.collection.Table;
import io.camunda.zeebe.util.micrometer.EnumMeter;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class TopologyMetrics {

  private final MeterRegistry registry;
  private final AtomicLong topologyVersion;
  private final AtomicLong changeId;
  private final AtomicLong changeVersion;
  private final AtomicLong pendingOperations;
  private final AtomicLong completedOperations;
  private final Map<String, Timer> operationDuration;
  private final Table<String, String, Counter> operationAttempts;
  private final EnumMeter<Status> changeStatus;

  public TopologyMetrics(final MeterRegistry registry) {
    this.registry = registry;
    topologyVersion = makeGauge(TOPOLOGY_VERSION);
    changeId = makeGauge(CHANGE_ID);
    changeStatus =
        EnumMeter.register(
            ClusterChangePlan.Status.class,
            CHANGE_STATUS,
            TopologyMetricsKeyName.CLUSTER_CHANGE_STATUS,
            registry);
    changeVersion = makeGauge(CHANGE_VERSION);
    pendingOperations = makeGauge(PENDING_OPERATIONS);
    completedOperations = makeGauge(COMPLETED_OPERATIONS);
    operationDuration = new HashMap<>();
    operationAttempts = Table.simple();
  }

  private Timer registerOperation(final String operation) {
    return Timer.builder(OPERATION_DURATION.getName())
        .description(OPERATION_DURATION.getDescription())
        .serviceLevelObjectives(OPERATION_DURATION.getTimerSLOs())
        .tags(TopologyMetricsKeyName.OPERATION.asString(), operation)
        .register(registry);
  }

  private Counter registerAttempt(final String operation, final String outcome) {
    return Counter.builder(OPERATION_ATTEMPTS.getName())
        .description(OPERATION_DURATION.getDescription())
        .tags(
            TopologyMetricsKeyName.OPERATION.asString(),
            operation,
            TopologyMetricsKeyName.OUTCOME.asString(),
            outcome)
        .register(registry);
  }

  private AtomicLong makeGauge(final TopologyMetricsDoc meter) {
    final var value = new AtomicLong();
    Gauge.builder(meter.getName(), value::get)
        .description(meter.getDescription())
        .register(registry);
    return value;
  }

  public void updateFromTopology(final ClusterConfiguration topology) {
    topologyVersion.set(topology.version());
    changeStatus.state(
        topology
            .pendingChanges()
            .map(ClusterChangePlan::status)
            .or(() -> topology.lastChange().map(CompletedChange::status))
            .orElse(Status.COMPLETED));
    changeId.set(topology.pendingChanges().map(ClusterChangePlan::id).orElse(0L));
    changeVersion.set(topology.pendingChanges().map(ClusterChangePlan::version).orElse(0));
    pendingOperations.set(
        topology
            .pendingChanges()
            .map(ClusterChangePlan::pendingOperations)
            .map(List::size)
            .orElse(0));
    completedOperations.set(
        topology
            .pendingChanges()
            .map(ClusterChangePlan::completedOperations)
            .map(List::size)
            .orElse(0));
  }

  public OperationObserver observeOperation(final ClusterConfigurationChangeOperation operation) {
    return startOperation(operation);
  }

  OperationObserver startOperation(final ClusterConfigurationChangeOperation operation) {
    final var timer =
        operationDuration.computeIfAbsent(
            operation.getClass().getSimpleName(), this::registerOperation);
    return new OperationObserver(operation, MicrometerUtil.timer(timer, Timer.start(registry)));
  }

  public final class OperationObserver {
    private final ClusterConfigurationChangeOperation operation;
    private final CloseableSilently timer;

    private OperationObserver(
        final ClusterConfigurationChangeOperation operation, final CloseableSilently timer) {
      this.operation = operation;
      this.timer = timer;
    }

    public void failed() {
      timer.close();
      operationAttempts
          .computeIfAbsent(
              operation.getClass().getSimpleName(),
              Outcome.FAILED.getName(),
              TopologyMetrics.this::registerAttempt)
          .increment();
    }

    public void applied() {
      timer.close();
      operationAttempts
          .computeIfAbsent(
              operation.getClass().getSimpleName(),
              Outcome.APPLIED.getName(),
              TopologyMetrics.this::registerAttempt)
          .increment();
    }
  }
}
