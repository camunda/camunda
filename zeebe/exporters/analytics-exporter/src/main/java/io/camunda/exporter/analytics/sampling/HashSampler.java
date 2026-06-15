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

  // Selected via JMH benchmark (HashSamplerBenchmark). Stafford Mix13 has the best
  // avalanche properties among the candidates while being competitive in throughput.
  private static long mix(final long x) {
    return mixStafford(x);
  }

  // Candidate mix functions — package-private so the JMH benchmark can compare them.

  static long mixStafford(long x) {
    x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
    x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
    x = x ^ (x >>> 31);
    return x;
  }

  static long mixMurmur3(long x) {
    x ^= x >>> 33;
    x *= 0xff51afd7ed558ccdL;
    x ^= x >>> 33;
    x *= 0xc4ceb9fe1a85ec53L;
    x ^= x >>> 33;
    return x;
  }

  static long mixMultiplyShift(long x) {
    x = x * 0x9E3779B97F4A7C15L;
    x = x ^ (x >>> 32);
    return x;
  }

  static long mixFnv1a(long x) {
    x ^= x >>> 23;
    x *= 0x2127599bf4325c37L;
    x ^= x >>> 47;
    return x;
  }
}
