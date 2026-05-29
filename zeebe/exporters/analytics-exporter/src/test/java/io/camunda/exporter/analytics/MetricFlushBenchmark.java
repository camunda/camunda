/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Worst-case benchmark: measures collectAndExport() with many metrics and high cardinality. Not a
 * JMH benchmark — uses System.nanoTime for rough timing.
 *
 * <p>Run: {@code mvn test -pl zeebe/exporters/analytics-exporter -Dtest=MetricFlushBenchmark
 * -DskipTests=false -Dbenchmark=true}
 */
class MetricFlushBenchmark {

  private static final MetricExporter NOOP_EXPORTER =
      new MetricExporter() {
        @Override
        public CompletableResultCode export(final Collection<MetricData> metrics) {
          return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
          return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
          return CompletableResultCode.ofSuccess();
        }

        @Override
        public AggregationTemporality getAggregationTemporality(
            final InstrumentType instrumentType) {
          return AggregationTemporalitySelector.deltaPreferred()
              .getAggregationTemporality(instrumentType);
        }
      };

  @Test
  @EnabledIfSystemProperty(named = "benchmark", matches = "true")
  void measureFlushPerformance() {
    final int[] metricCounts = {1, 10, 50, 100, 1000};
    final int dimensionsPerMetric = 1999; // SDK cardinality limit
    final int warmupRuns = 3;
    final int measurementRuns = 5;

    System.out.printf(
        "%n=== MetricFlushBenchmark: collectAndExport() with %d dimensions per metric ===%n%n",
        dimensionsPerMetric);
    System.out.printf(
        "%-12s %-12s %-12s %-15s%n", "Metrics", "Dimensions", "Flush (ms)", "Data points");

    for (final int metricCount : metricCounts) {
      final var reader = new ManualMetricReader(NOOP_EXPORTER);
      final var provider = SdkMeterProvider.builder().registerMetricReader(reader).build();
      final var meter = provider.get("bench");

      // Create counters and populate with dimensions
      for (int m = 0; m < metricCount; m++) {
        final var counter = meter.counterBuilder("metric." + m).build();
        for (int d = 0; d < dimensionsPerMetric; d++) {
          counter.add(
              1,
              Attributes.of(
                  AttributeKey.stringKey("process.id"), "process-" + d,
                  AttributeKey.longKey("version"), (long) (d % 10),
                  AttributeKey.stringKey("tenant"), "tenant-" + (d % 50)));
        }
      }

      // Warmup
      for (int i = 0; i < warmupRuns; i++) {
        reader.collectAndExport();
        // Re-populate for next run
        for (int m = 0; m < metricCount; m++) {
          final var counter = meter.counterBuilder("metric." + m).build();
          for (int d = 0; d < dimensionsPerMetric; d++) {
            counter.add(
                1,
                Attributes.of(
                    AttributeKey.stringKey("process.id"), "process-" + d,
                    AttributeKey.longKey("version"), (long) (d % 10),
                    AttributeKey.stringKey("tenant"), "tenant-" + (d % 50)));
          }
        }
      }

      // Measure
      long totalNs = 0;
      for (int i = 0; i < measurementRuns; i++) {
        // Re-populate
        for (int m = 0; m < metricCount; m++) {
          final var counter = meter.counterBuilder("metric." + m).build();
          for (int d = 0; d < dimensionsPerMetric; d++) {
            counter.add(
                1,
                Attributes.of(
                    AttributeKey.stringKey("process.id"), "process-" + d,
                    AttributeKey.longKey("version"), (long) (d % 10),
                    AttributeKey.stringKey("tenant"), "tenant-" + (d % 50)));
          }
        }

        final long start = System.nanoTime();
        reader.collectAndExport();
        totalNs += System.nanoTime() - start;
      }

      final double avgMs = (totalNs / (double) measurementRuns) / 1_000_000.0;
      final int expectedDataPoints = metricCount * Math.min(dimensionsPerMetric, 2000);

      System.out.printf(
          "%-12d %-12d %-12.2f %-15d%n",
          metricCount, dimensionsPerMetric, avgMs, expectedDataPoints);

      provider.shutdown();
    }

    System.out.println();
  }
}
