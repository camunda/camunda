/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.benchmark;

public class MetricsReader {
  private static final int DEFAULT_PARTITION_ID = 1;

  private final PrometheusClient prometheusClient;

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

  public long getRecordsNotExported() {
    final var metrics = prometheusClient.fetchMetrics();

    final var lastCommitted =
        metrics.stream()
            .filter(p -> p.name().equals("zeebe_log_appender_last_committed_position"))
            .findFirst()
            .map(PrometheusMetric::value)
            .orElse(0d);

    final var lastExported =
        metrics.stream()
            .filter(p -> p.name().equals("zeebe_exporter_last_exported_position"))
            .findFirst()
            .map(PrometheusMetric::value)
            .orElse(0d);

    System.out.println("lastCommitted: " + lastCommitted + ", lastExported: " + lastExported);

    return (long) (lastCommitted - lastExported);
  }
}
