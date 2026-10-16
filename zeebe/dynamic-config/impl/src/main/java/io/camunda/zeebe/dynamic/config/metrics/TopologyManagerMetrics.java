/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.metrics;

import static io.camunda.zeebe.dynamic.config.metrics.TopologyManagerMetricsDoc.*;

import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.collection.Table;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.HashMap;
import java.util.Map;

public class TopologyManagerMetrics {

  private final Map<String, Timer> operationDuration;
  private final Table<String, String, Counter> operationAttempts;
  private final MeterRegistry registry;

  public TopologyManagerMetrics(final MeterRegistry registry) {
    this.registry = registry;
    operationDuration = new HashMap<>();
    operationAttempts = Table.simple();
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

  private Timer registerOperation(final String operation) {
    return Timer.builder(OPERATION_DURATION.getName())
        .description(OPERATION_DURATION.getDescription())
        .serviceLevelObjectives(OPERATION_DURATION.getTimerSLOs())
        .tag(TopologyManagerMetricsKeyName.OPERATION.asString(), operation)
        .register(registry);
  }

  private Counter registerAttempt(final String operation, final String outcome) {
    return Counter.builder(OPERATION_ATTEMPTS.getName())
        .description(OPERATION_DURATION.getDescription())
        .tags(
            TopologyManagerMetricsKeyName.OPERATION.asString(),
            operation,
            TopologyManagerMetricsKeyName.OUTCOME.asString(),
            outcome)
        .register(registry);
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
              TopologyManagerMetrics.this::registerAttempt)
          .increment();
    }

    public void applied() {
      timer.close();
      operationAttempts
          .computeIfAbsent(
              operation.getClass().getSimpleName(),
              Outcome.APPLIED.getName(),
              TopologyManagerMetrics.this::registerAttempt)
          .increment();
    }
  }
}
