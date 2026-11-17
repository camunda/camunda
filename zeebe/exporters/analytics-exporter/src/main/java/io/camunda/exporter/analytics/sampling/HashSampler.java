/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics.sampling;

/**
 * Deterministic, stateless sampler. Bit-mixing distributes log positions uniformly so sampling
 * works regardless of how sparse or clustered the relevant events are. See {@code
 * docs/sampling-explainer.md} for the full design rationale.
 */
public final class HashSampler {

  /** Minimum sample rate: no records are sampled. */
  public static final double MIN_SAMPLE_RATE = 0.0;

  /** Maximum sample rate: all records are sampled. */
  public static final double MAX_SAMPLE_RATE = 1.0;

  /**
   * Resolution of the sampling bucket. A value of 10_000 gives 0.01 % granularity (one part in ten
   * thousand).
   */
  static final long SAMPLING_RESOLUTION = 10_000L;

  private HashSampler() {}

  public static boolean shouldSample(final long position, final double rate) {
    if (rate >= MAX_SAMPLE_RATE) {
      return true;
    }
    if (rate <= MIN_SAMPLE_RATE) {
      return false;
    }
    return (mix(position) & Long.MAX_VALUE) % SAMPLING_RESOLUTION
        < (long) (rate * SAMPLING_RESOLUTION);
  }

  // Stafford Mix13 — selected via JMH benchmark (HashSamplerBenchmark) over Murmur3,
  // multiply-shift, and FNV-1a for best avalanche properties at competitive throughput.
  private static long mix(long x) {
    x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
    x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
    x = x ^ (x >>> 31);
    return x;
  }
}
