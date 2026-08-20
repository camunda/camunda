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
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Worst-case benchmark for {@code collectAndExport()} with varying metric counts and high
 * cardinality (1999 dimensions per metric — the SDK default limit).
 *
 * <p>Run: {@code mvn verify -pl zeebe/exporters/analytics-exporter -Dtest=MetricFlushBenchmark
 * -DskipTests=false -Dbenchmark=true}
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class MetricFlushBenchmark {

  private static final int DIMENSIONS_PER_METRIC = 1999;

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

  @Param({"1", "10", "50", "100", "1000"})
  private int metricCount;

  private ManualMetricReader reader;
  private SdkMeterProvider provider;
  private LongCounter[] counters;

  @Setup(Level.Trial)
  public void setUp() {
    reader = new ManualMetricReader(NOOP_EXPORTER);
    provider = SdkMeterProvider.builder().registerMetricReader(reader).build();
    final var meter = provider.get("bench");
    counters = new LongCounter[metricCount];
    for (int m = 0; m < metricCount; m++) {
      counters[m] = meter.counterBuilder("metric." + m).build();
    }
    populateCounters();
    reader.collectAndExport();
  }

  @Setup(Level.Iteration)
  public void populateCounters() {
    for (final LongCounter counter : counters) {
      for (int d = 0; d < DIMENSIONS_PER_METRIC; d++) {
        counter.add(
            1,
            Attributes.of(
                AttributeKey.stringKey("process.id"), "process-" + d,
                AttributeKey.longKey("version"), (long) (d % 10),
                AttributeKey.stringKey("tenant"), "tenant-" + (d % 50)));
      }
    }
  }

  @Benchmark
  public CompletableResultCode collectAndExport() {
    return reader.collectAndExport();
  }

  @TearDown
  public void tearDown() {
    provider.shutdown();
  }

  @Test
  @EnabledIfSystemProperty(named = "benchmark", matches = "true")
  void runBenchmarks() throws Exception {
    final var options =
        new org.openjdk.jmh.runner.options.OptionsBuilder()
            .include(MetricFlushBenchmark.class.getSimpleName())
            .warmupIterations(3)
            .measurementIterations(5)
            .forks(0)
            .build();
    new org.openjdk.jmh.runner.Runner(options).run();
  }
}
