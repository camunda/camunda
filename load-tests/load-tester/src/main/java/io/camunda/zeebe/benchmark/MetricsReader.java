/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.benchmark;

import com.google.common.util.concurrent.AtomicDouble;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class MetricsReader {

  private static final Set<String> EXPORTERS = Set.of("rdbmsexporter", "camundaexporter");

  private final PrometheusClient prometheusClient;

  private final AtomicDouble lastReceivedTotal = new AtomicDouble(0.0);
  private final AtomicDouble lastDroppedTotal = new AtomicDouble(0.0);
  private final AtomicReference<Instant> lastBackpressureCall =
      new AtomicReference<>(Instant.now());

  public MetricsReader(final String prometheusUrl) {
    prometheusClient = new PrometheusClient(prometheusUrl);
  }

  public double getCurrentCpuLoad() {
    return prometheusClient.fetchMetrics().stream()
        .filter(p -> p.name().equals("process_cpu_usage"))
        .findFirst()
        .map(PrometheusMetric::value)
        .orElse(-1.0);
  }

  public double getTotalCreatedProcessInstances() {
    return prometheusClient.fetchMetrics().stream()
        .filter(p -> p.name().equals("zeebe_executed_instances_total"))
        .map(PrometheusMetric::value)
        .reduce(Double::sum)
        .orElse(0.0);
  }

  public double getClusterLoad() {
    return prometheusClient.fetchMetrics().stream()
        .filter(p -> p.name().equals("zeebe_flow_control_partition_load"))
        .map(PrometheusMetric::value)
        .max(Double::compare)
        .orElse(0.0);
  }

  public double getBackpressureRate() {
    final var metrics = prometheusClient.fetchMetrics();
    final double currentDroppedTotal =
        metrics.stream()
            .filter(p -> p.name().equals("zeebe_dropped_request_count_total"))
            .map(PrometheusMetric::value)
            .max(Double::compare)
            .orElse(0d);

    final double currentReceivedTotal =
        metrics.stream()
            .filter(p -> p.name().equals("zeebe_received_request_count_total"))
            .map(PrometheusMetric::value)
            .max(Double::compare)
            .orElse(0d);

    if (currentReceivedTotal - lastReceivedTotal.get() == 0) {
      return 0.0;
    }

    // return backpressure rate since last call
    final double backpressureRate =
        (currentDroppedTotal - lastDroppedTotal.getAndSet(currentDroppedTotal))
            / (currentReceivedTotal - lastReceivedTotal.getAndSet(currentReceivedTotal));

    System.out.println(
        "Backpressure rate: "
            + backpressureRate
            + " (dropped: "
            + (currentDroppedTotal - lastDroppedTotal.get())
            + ", received: "
            + (currentReceivedTotal - lastReceivedTotal.get())
            + ")");
    return backpressureRate;
  }

  public long getRecordsNotExported() {
    final var metrics = prometheusClient.fetchMetrics();

    final var lastCommitted =
        metrics.stream()
            .filter(p -> p.name().equals("zeebe_log_appender_last_committed_position"))
            .map(PrometheusMetric::value)
            .max(Double::compare)
            .orElse(0d);

    final var lastExported =
        metrics.stream()
            .filter(p -> p.name().equals("zeebe_exporter_last_exported_position"))
            .filter(p -> EXPORTERS.contains(p.labels().get("exporter")))
            .map(PrometheusMetric::value)
            .findFirst()
            .orElse(0d);

    System.out.println(
        "lastCommitted: "
            + lastCommitted
            + ", lastExported: "
            + lastExported
            + ", not exported: "
            + (lastCommitted - lastExported));

    return (long) (lastCommitted - lastExported);
  }
}
