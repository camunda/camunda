/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmarks the hot-path cost of {@link MicrometerMeterProvider} counter recording.
 *
 * <p>Two attribute scenarios expose qualitatively different code paths:
 *
 * <ul>
 *   <li>Empty attributes — the real hot path ({@code otel.sdk.log.created} carries no attributes).
 *       After the first call the Counter is cached; subsequent calls cost one ConcurrentHashMap.get
 *       + DoubleAdder.add with no allocation.
 *   <li>N attributes — worst-case allocation path: {@code toMicrometerTags()} builds an ArrayList +
 *       Tags + String[] on every call before the ConcurrentHashMap lookup. The Counter itself is
 *       still cached after the first call per unique tag set.
 * </ul>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@State(Scope.Benchmark)
public class MicrometerMeterProviderBenchmark {

  @Param({"0", "5", "20"})
  int attrCount;

  private LongCounter noopCounter;
  private LongCounter bridgeCounter;
  private Attributes attrs;

  @Setup
  public void setup() {
    noopCounter = OpenTelemetry.noop().getMeter("noop").counterBuilder("test").build();
    bridgeCounter =
        new MicrometerMeterProvider(new SimpleMeterRegistry())
            .meterBuilder("test")
            .build()
            .counterBuilder("otel.sdk.log.created")
            .build();
    final AttributesBuilder b = Attributes.builder();
    for (int i = 0; i < attrCount; i++) {
      b.put("key." + i, "value" + i);
    }
    attrs = b.build();
  }

  @Benchmark
  public void measureNoopCounter() {
    noopCounter.add(1, attrs);
  }

  @Benchmark
  public void measureBridgeCounter() {
    bridgeCounter.add(1, attrs);
  }
}
