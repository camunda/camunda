/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.microbenchmarks.scheduler;

import io.camunda.zeebe.scheduler.cpu.ThreadGroupCpuSampler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Measures the cost of one {@link ThreadGroupCpuSampler#sample()} pass over thread groups with idle
 * threads.
 *
 * <p>Besides the group threads, the JVM runs {@code otherThreads} parked threads that belong to no
 * registered group: the cost of a pass depends on the number of all live threads, and a broker has
 * over a hundred of them next to the threads of the groups.
 */
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class ThreadGroupCpuSamplerBenchmark {

  @Param({"20", "50"})
  public int groups;

  @Param({"2", "4"})
  public int threadsPerGroup;

  @Param({"0", "200"})
  public int otherThreads;

  private final CountDownLatch release = new CountDownLatch(1);
  private final List<Thread> threads = new ArrayList<>();
  private ThreadGroupCpuSampler sampler;

  @Setup
  public void setup() {
    sampler = new ThreadGroupCpuSampler(Duration.ofSeconds(1), Duration.ofSeconds(10));
    for (int i = 0; i < otherThreads; i++) {
      startParked(null);
    }
    for (int t = 0; t < groups; t++) {
      final var group = new ThreadGroup("group-" + t);
      sampler.register("group-" + t, group, 1);
      for (int i = 0; i < threadsPerGroup; i++) {
        startParked(group);
      }
    }
  }

  private void startParked(final ThreadGroup group) {
    final var thread =
        new Thread(
            group,
            () -> {
              try {
                release.await();
              } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });
    thread.setDaemon(true);
    thread.start();
    threads.add(thread);
  }

  @TearDown
  public void tearDown() throws InterruptedException {
    release.countDown();
    for (final var thread : threads) {
      thread.join();
    }
  }

  @Benchmark
  public void samplePass() {
    sampler.sample();
  }
}
