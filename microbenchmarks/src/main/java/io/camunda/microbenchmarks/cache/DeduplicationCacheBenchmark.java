/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.microbenchmarks.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

/**
 * Microbenchmark comparing two deduplication strategies on a shared Caffeine cache:
 *
 * <ul>
 *   <li><b>putIfAbsent</b> – single atomic CAS via {@code cache.asMap().putIfAbsent(key, PRESENT)}
 *   <li><b>getThenPut</b> – explicit {@code getIfPresent} followed by {@code put}
 * </ul>
 *
 * Rationale:
 *
 * <ul>
 *   <li>Across multiple runs and key distributions (5 000 / 9 500 keys), {@code putIfAbsent} was
 *       consistently a bit faster (≈20–25 ns/op) or within noise of {@code getThenPut}.
 *   <li>{@code putIfAbsent} is a single atomic operation on the underlying map and is therefore
 *       race-free under concurrent access.
 *   <li>{@code getThenPut} introduces a read–write race window and showed higher latency variance
 *       in the benchmarks.
 * </ul>
 *
 * Conclusion: we use {@code putIfAbsent} in {@code DeduplicationCache} for production code. The
 * {@code getThenPut} variant is kept here only as a comparison baseline in this benchmark.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
public class DeduplicationCacheBenchmark {

  private static final Boolean PRESENT = Boolean.TRUE;

  // Variant 1 — current implementation: single atomic CAS via asMap().putIfAbsent.
  public static boolean isFirstOccurrence(final String key, final Cache<String, Boolean> cache) {
    return cache.asMap().putIfAbsent(key, PRESENT) == null;
  }

  // Variant 2 — explicit getIfPresent + put (kept for benchmarking reference only).
  public static boolean isFirstOccurrence2(final String key, final Cache<String, Boolean> cache) {
    if (cache.getIfPresent(key) == null) {
      cache.put(key, PRESENT);
      return true;
    }
    return false;
  }

  @Benchmark
  public boolean benchmarkPutIfAbsent(final CacheState state) {
    return isFirstOccurrence(state.nextKey(), state.cache);
  }

  @Benchmark
  public boolean benchmarkGetThenPut(final CacheState state) {
    return isFirstOccurrence2(state.nextKey(), state.cache);
  }

  @State(Scope.Thread)
  public static class CacheState {

    @Param({"10000"})
    public int maxSize;

    @Param({"5000", "9500"})
    public int uniqueKeys;

    Cache<String, Boolean> cache;
    String[] keys;
    int index;

    @Setup(Level.Trial)
    public void setup() {
      cache =
          Caffeine.newBuilder().maximumSize(maxSize).expireAfterWrite(30, TimeUnit.MINUTES).build();

      keys = new String[uniqueKeys];
      for (int i = 0; i < uniqueKeys; i++) {
        keys[i] = "key-" + i;
      }
      index = 0;
    }

    String nextKey() {
      // simple, fast cyclic access – avoids RNG overhead in the benchmark
      final String key = keys[index];
      index++;
      if (index == keys.length) {
        index = 0;
      }
      return key;
    }
  }
}
