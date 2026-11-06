/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.microbenchmarks.scheduler;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Measures the cost of creating, awaiting and completing a {@link CompletableActorFuture}. Futures
 * are usually awaited by a single callback ({@code andThen}, {@code thenApply}, {@code
 * runOnCompletion}), so that case is covered explicitly next to the none and many callbacks cases.
 *
 * <p>Allocation is the main cost, run with {@code -prof gc} and compare {@code gc.alloc.rate.norm}
 * (bytes per operation), e.g. {@code java -jar target/benchmarks.jar
 * CompletableActorFutureBenchmark -prof gc}.
 *
 * <p>The callbacks run inline, so no actor scheduler is involved and only the future itself is
 * measured.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
// CompletableActorFuture uses Agrona's UnsafeApi, which needs access to jdk.internal.misc
@Fork(value = 2, jvmArgsAppend = "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED")
public class CompletableActorFutureBenchmark {

  private static final Executor INLINE = Runnable::run;
  private static final Long VALUE = 42L;

  @Benchmark
  public void completeWithoutCallback(final Blackhole blackhole) {
    final var future = new CompletableActorFuture<Long>();
    future.complete(VALUE);
    blackhole.consume(future);
  }

  @Benchmark
  public void completeWithOneCallback(final Blackhole blackhole) {
    final var future = new CompletableActorFuture<Long>();
    future.onComplete((value, error) -> blackhole.consume(value), INLINE);
    future.complete(VALUE);
    blackhole.consume(future);
  }

  @Benchmark
  public void completeWithThreeCallbacks(final Blackhole blackhole) {
    final var future = new CompletableActorFuture<Long>();
    for (int i = 0; i < 3; i++) {
      future.onComplete((value, error) -> blackhole.consume(value), INLINE);
    }
    future.complete(VALUE);
    blackhole.consume(future);
  }

  @Benchmark
  public void thenApply(final Blackhole blackhole) {
    final var future = new CompletableActorFuture<Long>();
    final ActorFuture<Long> next = future.thenApply(value -> value + 1, INLINE);
    future.complete(VALUE);
    blackhole.consume(next);
  }

  @Benchmark
  public void andThen(final Blackhole blackhole) {
    final var future = new CompletableActorFuture<Long>();
    final ActorFuture<Long> next =
        future.andThen(value -> CompletableActorFuture.completed(value + 1), INLINE);
    future.complete(VALUE);
    blackhole.consume(next);
  }

  /** A chain of three dependent stages, each awaited by exactly one callback. */
  @Benchmark
  public void thenApplyChain(final Blackhole blackhole) {
    final var future = new CompletableActorFuture<Long>();
    final ActorFuture<Long> last =
        future
            .thenApply(value -> value + 1, INLINE)
            .thenApply(value -> value + 1, INLINE)
            .thenApply(value -> value + 1, INLINE);
    future.complete(VALUE);
    blackhole.consume(last);
  }
}
