/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.functional;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerExtension;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Implementation of the <a href="https://github.com/atemerev/skynet">Skynet</a> concurrency
 * benchmark: a root actor spawns 10 children, each of which spawns 10 more, until a total of
 * 1,000,000 leaf actors exist. Every leaf returns its ordinal number and every parent returns the
 * sum of its children's results, so the root must end up with the sum of 0..999,999.
 *
 * <p>This stresses spawning, scheduling and tearing down a very large number of short-lived actors
 * as well as future completion across actors.
 */
final class SkynetTest {

  private static final int FAN_OUT = 10;
  private static final long LEAF_COUNT = 1_000_000;

  @RegisterExtension
  private final ActorSchedulerExtension actorScheduler = new ActorSchedulerExtension();

  @Test
  void shouldSumOneMillionLeafActors() {
    // given
    final var scheduler = actorScheduler.get();
    final var root = new SkynetActor(scheduler, 0, LEAF_COUNT);

    // when
    final long start = System.nanoTime();
    scheduler.submitActor(root);
    final ActorFuture<Long> result = root.result();

    // then
    assertThat(result).succeedsWithin(Duration.ofMinutes(2)).isEqualTo(expectedSum(LEAF_COUNT));
    System.out.printf(
        "Skynet: summed %d leaf actors in %d ms%n",
        LEAF_COUNT, Duration.ofNanos(System.nanoTime() - start).toMillis());
  }

  @ParameterizedTest
  @ValueSource(longs = {1, 10, 1_000, 10_000, 100_000})
  void shouldSumSmallTree(final long leafCount) {
    // given
    final var scheduler = actorScheduler.get();
    final var root = new SkynetActor(scheduler, 0, leafCount);

    // when
    final long start = System.nanoTime();
    scheduler.submitActor(root);

    // then
    assertThat(root.result())
        .succeedsWithin(Duration.ofSeconds(60))
        .isEqualTo(expectedSum(leafCount));
    System.out.printf("Skynet %d: %d ms%n", leafCount, (System.nanoTime() - start) / 1_000_000);
  }

  private static long expectedSum(final long leafCount) {
    return leafCount * (leafCount - 1) / 2;
  }

  /**
   * Actor covering the leaf numbers {@code [first, first + size)}. A leaf completes with its
   * number, an inner actor spawns {@link #FAN_OUT} children and completes with the sum of theirs.
   */
  private static final class SkynetActor extends Actor {
    private final ActorScheduler scheduler;
    private final long first;
    private final long size;
    private final CompletableActorFuture<Long> result = new CompletableActorFuture<>();

    private SkynetActor(final ActorScheduler scheduler, final long first, final long size) {
      this.scheduler = scheduler;
      this.first = first;
      this.size = size;
    }

    ActorFuture<Long> result() {
      return result;
    }

    @Override
    protected void onActorStarted() {
      if (size == 1) {
        complete(first);
        return;
      }

      final long childSize = size / FAN_OUT;
      final List<ActorFuture<Long>> childResults = new ArrayList<>(FAN_OUT);
      for (int i = 0; i < FAN_OUT; i++) {
        final var child = new SkynetActor(scheduler, first + i * childSize, childSize);
        scheduler.submitActor(child);
        childResults.add(child.result());
      }

      actor.runOnCompletion(
          childResults,
          error -> {
            if (error != null) {
              result.completeExceptionally(error);
              actor.close();
              return;
            }
            long sum = 0;
            for (final var childResult : childResults) {
              sum += childResult.join();
            }
            complete(sum);
          });
    }

    private void complete(final long value) {
      result.complete(value);
      actor.close();
    }
  }
}
