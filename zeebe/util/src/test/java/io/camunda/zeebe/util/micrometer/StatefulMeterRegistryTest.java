/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.micrometer;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class StatefulMeterRegistryTest {
  private static final Logger LOG = LoggerFactory.getLogger(StatefulMeterRegistryTest.class);

  @AutoClose private MeterRegistry wrapped = new SimpleMeterRegistry();
  @AutoClose private final StatefulMeterRegistry registry = new StatefulMeterRegistry(wrapped);

  @Test
  void shouldRemoveStatefulGauge() {
    // given
    final var gauge = StatefulGauge.builder("test").register(registry);
    gauge.increment();

    // when
    registry.remove(gauge);

    // then - re-registering should produce a different stateful gauge instance
    final var duplicate = StatefulGauge.builder("test").register(registry);
    assertThat(duplicate).isNotSameAs(gauge);
  }

  @Test
  void shouldReturnSameStatefulGauge() {
    // given
    final var first = StatefulGauge.builder("test").register(registry);
    first.increment();

    // when
    final var second = StatefulGauge.builder("test").register(registry);

    // then
    assertThat(second).isSameAs(first);
    assertThat(second.value()).isOne();
  }

  /** See issue: <a href="https://github.com/camunda/camunda/issues/33941">...</a> */
  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void shouldNotDeadlockWithManyThreadsRegisteringAndRemoving() {
    final int numThreads = 10;
    final int numGauges = 50;
    final int numIterations = 30;
    final var simpleMeterRegistry = new SimpleMeterRegistry();
    final var meterRegistry = new StatefulMeterRegistry(simpleMeterRegistry);
    final ExecutorService executor = Executors.newFixedThreadPool(numThreads);

    try {
      final List<CompletableFuture<Void>> futures = new ArrayList<>();

      // Create registering tasks
      for (int threadId = 0; threadId < numThreads / 2; threadId++) {
        final int id = threadId;
        futures.add(
            CompletableFuture.runAsync(
                () -> {
                  for (int iter = 0; iter < numIterations; iter++) {
                    for (int gaugeIndex = 0; gaugeIndex < numGauges; gaugeIndex++) {
                      registerGauge(gaugeIndex, id, iter, meterRegistry);
                    }
                  }
                },
                executor));
      }

      // Create removing tasks
      for (int threadId = 0; threadId < numThreads / 2; threadId++) {
        final int id = threadId;
        futures.add(
            CompletableFuture.runAsync(
                () -> {
                  for (int iter = 0; iter < numIterations; iter++) {
                    for (int gaugeIndex = 0; gaugeIndex < numGauges; gaugeIndex++) {
                      removeGauge(gaugeIndex, id, iter, meterRegistry);
                    }
                  }
                },
                executor));
      }

      try {
        // Wait for all futures to complete
        final var allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        allFutures.get();
      } catch (final Exception e) {
        throw new AssertionError("Test failed due to exception: " + e.getMessage(), e);
      }
    } finally {
      // Check for deadlocked threads using JVM's ThreadMXBean
      checkForDeadlock();

      executor.shutdownNow();
      meterRegistry.close();
      simpleMeterRegistry.close();
    }
  }

  private void checkForDeadlock() {
    final ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    final long[] deadlockedThreads = bean.findDeadlockedThreads();

    if (deadlockedThreads != null) {
      final StringBuilder sb = new StringBuilder("Deadlock detected by JVM ThreadMXBean:\n");
      for (final long id : deadlockedThreads) {
        sb.append("Deadlocked Thread ID: ").append(id).append("\n");
        sb.append(bean.getThreadInfo(id)).append("\n");
      }
      LOG.error(sb.toString());
      throw new AssertionError(sb.toString());
    }
  }

  private void registerGauge(
      final int gaugeIndex,
      final int threadId,
      final int iteration,
      final StatefulMeterRegistry testRegistry) {
    final String gaugeName = "mt-gauge-" + gaugeIndex;
    LOG.debug(
        "Attempting to register gauge: {} (threadId={}, iteration={})",
        gaugeName,
        threadId,
        iteration);
    final var gauge = StatefulGauge.builder(gaugeName).register(testRegistry);
    gauge.set((long) threadId * 1000 + iteration);
    LOG.debug("Registered gauge: {} (threadId={}, iteration={})", gaugeName, threadId, iteration);
  }

  private void removeGauge(
      final int gaugeIndex,
      final int threadId,
      final int iteration,
      final StatefulMeterRegistry testRegistry) {
    final String gaugeName = "mt-gauge-" + gaugeIndex;
    LOG.debug(
        "Attempting to remove gauge: {} (threadId={}, iteration={})",
        gaugeName,
        threadId,
        iteration);
    final var search = testRegistry.find(gaugeName).gauge();
    if (search != null) {
      testRegistry.remove(search);
      LOG.debug("Removed gauge: {} (threadId={}, iteration={})", gaugeName, threadId, iteration);
    } else {
      LOG.debug(
          "Gauge not found for removal: {} (threadId={}, iteration={})",
          gaugeName,
          threadId,
          iteration);
    }
  }
}
