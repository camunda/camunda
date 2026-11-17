/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.metrics;

import static io.camunda.zeebe.broker.exporter.metrics.VariableMetricsDoc.VARIABLE_CREATED_SIZE;

import io.camunda.zeebe.broker.exporter.metrics.VariableMetricsDoc.VariableKeyNames;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class VariableMetrics {

  private final MeterRegistry meterRegistry;
  // Plain HashMap is sufficient: each MetricsExporter (and thus each VariableMetrics
  // instance) is owned by a single partition and accessed only on that partition's
  // processing thread.
  private final Map<String, DistributionSummary> createdSizeSummaries = new HashMap<>();

  public VariableMetrics(final MeterRegistry meterRegistry) {
    this.meterRegistry = Objects.requireNonNull(meterRegistry, "must specify a meter registry");
  }

  public void recordVariableCreated(final String bpmnProcessId, final int sizeBytes) {
    createdSizeSummaries
        .computeIfAbsent(bpmnProcessId, this::registerSizeSummary)
        .record(sizeBytes);
  }

  private DistributionSummary registerSizeSummary(final String bpmnProcessId) {
    return DistributionSummary.builder(VARIABLE_CREATED_SIZE.getName())
        .description(VARIABLE_CREATED_SIZE.getDescription())
        .serviceLevelObjectives(VARIABLE_CREATED_SIZE.getDistributionSLOs())
        .tag(VariableKeyNames.BPMN_PROCESS_ID.asString(), bpmnProcessId)
        .register(meterRegistry);
  }
}
