/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.metrics;

import static io.camunda.zeebe.broker.exporter.metrics.VariableMetricsDoc.VARIABLE_CREATED_BYTES;
import static io.camunda.zeebe.broker.exporter.metrics.VariableMetricsDoc.VARIABLE_CREATED_SIZE;

import io.camunda.zeebe.broker.exporter.metrics.VariableMetricsDoc.VariableKeyNames;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class VariableMetrics {

  private final MeterRegistry meterRegistry;
  private final Map<String, Counter> createdBytesCounters = new ConcurrentHashMap<>();
  private final Map<String, DistributionSummary> createdSizeSummaries = new ConcurrentHashMap<>();

  public VariableMetrics(final MeterRegistry meterRegistry) {
    this.meterRegistry = Objects.requireNonNull(meterRegistry, "must specify a meter registry");
  }

  public void recordVariableCreated(final String bpmnProcessId, final int sizeBytes) {
    createdBytesCounters
        .computeIfAbsent(bpmnProcessId, this::registerBytesCounter)
        .increment(sizeBytes);
    createdSizeSummaries
        .computeIfAbsent(bpmnProcessId, this::registerSizeSummary)
        .record(sizeBytes);
  }

  private Counter registerBytesCounter(final String bpmnProcessId) {
    return Counter.builder(VARIABLE_CREATED_BYTES.getName())
        .description(VARIABLE_CREATED_BYTES.getDescription())
        .tag(VariableKeyNames.BPMN_PROCESS_ID.asString(), bpmnProcessId)
        .register(meterRegistry);
  }

  private DistributionSummary registerSizeSummary(final String bpmnProcessId) {
    return DistributionSummary.builder(VARIABLE_CREATED_SIZE.getName())
        .description(VARIABLE_CREATED_SIZE.getDescription())
        .serviceLevelObjectives(VARIABLE_CREATED_SIZE.getDistributionSLOs())
        .tag(VariableKeyNames.BPMN_PROCESS_ID.asString(), bpmnProcessId)
        .register(meterRegistry);
  }
}
