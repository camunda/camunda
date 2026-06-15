/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics.sampling;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * JMH microbenchmarks comparing the four candidate mix functions in {@link HashSampler} and the
 * overall {@link HashSampler#shouldSample(long, double)} hot path.
 *
 * <p>Run via: {@code mvn verify -pl zeebe/exporters/analytics-exporter -Dtest=HashSamplerBenchmark
 * -DskipTests=false -Dbenchmark=true}
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
public class HashSamplerBenchmark {

  private long position;

  @Setup
  public void setUp() {
    // Pre-compute a representative position to avoid allocation noise inside benchmarks
    position = 42_000_001L;
  }

  // ---------------------------------------------------------------------------
  // Mix function candidates
  // ---------------------------------------------------------------------------

  @Benchmark
  public long mixStafford() {
    return HashSampler.mixStafford(position);
  }

  @Benchmark
  public long mixMurmur3() {
    return HashSampler.mixMurmur3(position);
  }

  @Benchmark
  public long mixMultiplyShift() {
    return HashSampler.mixMultiplyShift(position);
  }

  @Benchmark
  public long mixFnv1a() {
    return HashSampler.mixFnv1a(position);
  }

  // ---------------------------------------------------------------------------
  // Full shouldSample paths
  // ---------------------------------------------------------------------------

  /** Measures the normal (non-trivial) sampling path at a low rate. */
  @Benchmark
  public boolean shouldSampleWithRate() {
    return HashSampler.shouldSample(position, 0.01);
  }

  /** Measures the fast-path short-circuit when rate == 1.0 (no hashing performed). */
  @Benchmark
  public boolean shouldSampleAlwaysPath() {
    return HashSampler.shouldSample(position, 1.0);
  }

  /** No-op baseline — establishes the JMH call overhead floor. */
  @Benchmark
  public boolean baseline() {
    return true;
  }

  // ---------------------------------------------------------------------------
  // Test runner (skipped in CI; enable with -Dbenchmark=true)
  // ---------------------------------------------------------------------------

  /**
   * Run benchmarks from IntelliJ via the play button. Skipped in CI by the {@code
   * EnabledIfSystemProperty} condition — pass {@code -Dbenchmark=true} to enable.
   */
  @Test
  @EnabledIfSystemProperty(named = "benchmark", matches = "true")
  void runBenchmarks() throws Exception {
    final var builder =
        new OptionsBuilder()
            .include(HashSamplerBenchmark.class.getSimpleName())
            .warmupIterations(5)
            .measurementIterations(10)
            .resultFormat(ResultFormatType.JSON)
            .result("target/jmh-result.json")
            .forks(1);

    new Runner(builder.build()).run();
  }
}
