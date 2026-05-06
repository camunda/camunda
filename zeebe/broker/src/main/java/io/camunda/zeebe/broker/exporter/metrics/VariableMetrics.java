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
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class VariableMetrics {

  private final MeterRegistry meterRegistry;
  private final Map<MeterKey, Counter> createdBytesCounters = new ConcurrentHashMap<>();
  private final Map<MeterKey, DistributionSummary> createdSizeSummaries = new ConcurrentHashMap<>();

  public VariableMetrics(final MeterRegistry meterRegistry) {
    this.meterRegistry = Objects.requireNonNull(meterRegistry, "must specify a meter registry");
  }

  public void recordVariableCreated(
      final String bpmnProcessId, final String partitionId, final int sizeBytes) {
    final var key = new MeterKey(bpmnProcessId, partitionId);
    createdBytesCounters
        .computeIfAbsent(key, k -> registerBytesCounter(k.bpmnProcessId(), k.partitionId()))
        .increment(sizeBytes);
    createdSizeSummaries
        .computeIfAbsent(key, k -> registerSizeSummary(k.bpmnProcessId(), k.partitionId()))
        .record(sizeBytes);
  }

  private Counter registerBytesCounter(final String bpmnProcessId, final String partitionId) {
    return Counter.builder(VARIABLE_CREATED_BYTES.getName())
        .description(VARIABLE_CREATED_BYTES.getDescription())
        .tag(PartitionKeyNames.PARTITION.asString(), partitionId)
        .tag(VariableKeyNames.BPMN_PROCESS_ID.asString(), bpmnProcessId)
        .register(meterRegistry);
  }

  private DistributionSummary registerSizeSummary(
      final String bpmnProcessId, final String partitionId) {
    return DistributionSummary.builder(VARIABLE_CREATED_SIZE.getName())
        .description(VARIABLE_CREATED_SIZE.getDescription())
        .serviceLevelObjectives(VARIABLE_CREATED_SIZE.getDistributionSLOs())
        .tag(PartitionKeyNames.PARTITION.asString(), partitionId)
        .tag(VariableKeyNames.BPMN_PROCESS_ID.asString(), bpmnProcessId)
        .register(meterRegistry);
  }

  private record MeterKey(String bpmnProcessId, String partitionId) {}
}
