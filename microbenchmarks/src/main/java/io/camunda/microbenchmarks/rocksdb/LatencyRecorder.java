/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.microbenchmarks.rocksdb;

import java.util.Arrays;

/**
 * Records every sample of a window in a growable primitive array; percentiles are exact. Cheap
 * enough for the sample rates we see here (at most a few hundred thousand per window) and avoids
 * any bucketing error that would hide tail changes between configurations.
 */
final class LatencyRecorder {
  private long[] samples = new long[1 << 16];
  private int size;
  private long sum;

  void record(final long nanos) {
    if (size == samples.length) {
      samples = Arrays.copyOf(samples, size << 1);
    }
    samples[size++] = nanos;
    sum += nanos;
  }

  Snapshot snapshotAndReset() {
    final Snapshot snapshot;
    if (size == 0) {
      snapshot = Snapshot.EMPTY;
    } else {
      Arrays.sort(samples, 0, size);
      snapshot =
          new Snapshot(
              size,
              sum / (double) size / 1000.0,
              percentile(0.50),
              percentile(0.99),
              percentile(0.999),
              samples[size - 1] / 1000.0);
    }
    size = 0;
    sum = 0;
    return snapshot;
  }

  private double percentile(final double p) {
    final int idx = (int) Math.min(size - 1, Math.ceil(p * size) - 1);
    return samples[Math.max(0, idx)] / 1000.0;
  }

  /** Latencies in microseconds. */
  record Snapshot(long count, double mean, double p50, double p99, double p999, double max) {
    static final Snapshot EMPTY = new Snapshot(0, 0, 0, 0, 0, 0);
  }
}
