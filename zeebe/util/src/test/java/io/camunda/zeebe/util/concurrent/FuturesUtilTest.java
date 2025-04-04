/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.concurrent;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.util.concurrency.FuturesUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class FuturesUtilTest {
  @AutoClose private static final ExecutorService EXECUTOR = Executors.newWorkStealingPool();

  @Nested
  class TraverseIgnoring {
    @Test
    public void shouldReturnCompletedFutureWhenCollectionIsEmpty() {
      assertThat(
              FuturesUtil.traverseIgnoring(
                  List.of(), a -> CompletableFuture.supplyAsync(() -> null), EXECUTOR))
          .isCompleted();
    }

    @Test
    public void shouldRunAllFuturesSequentially() {
      // given
      final var count = 1000;
      final var map = new CopyOnWriteArrayList<>();
      final var elements = new ArrayList<Integer>(count);
      for (int i = 0; i < count; i++) {
        elements.add(i);
      }
      // when
      FuturesUtil.traverseIgnoring(
              elements, i -> CompletableFuture.runAsync(() -> map.add(i)), EXECUTOR)
          .join();
      // then
      assertThat(map).containsExactlyElementsOf(elements);
    }

    @Test
    public void shouldStopAndReturnErrorIfAFutureFails() {
      // given
      final var map = new ConcurrentHashMap<>();
      // when
      final var future =
          FuturesUtil.traverseIgnoring(
              List.of(1, 2, 3),
              i ->
                  i % 2 == 0
                      ? CompletableFuture.failedFuture(new RuntimeException("Expected"))
                      : CompletableFuture.runAsync(() -> map.put(i, i)),
              EXECUTOR);
      // then
      assertThat(future)
          .failsWithin(Duration.ofSeconds(5))
          .withThrowableThat()
          .withMessageContaining("Expected");
      // does not contain 3 as the computation is not spawned
      assertThat(map.keySet()).containsExactly(1);
    }
  }

  @Nested
  class ParTraversse {

    @Test
    public void shouldReturnCompletedFutureWhenCollectionIsEmpty() {
      assertThat(FuturesUtil.parTraverse(List.of(), a -> CompletableFuture.supplyAsync(() -> null)))
          .isCompleted();
    }

    @Test
    public void shouldRunAllFuturesSequentially() {
      // given
      final var count = 1000;
      final var map = new CopyOnWriteArrayList<>();
      final var elements = new ArrayList<Integer>(count);
      for (int i = 0; i < count; i++) {
        elements.add(i);
      }
      // when
      FuturesUtil.parTraverse(elements, i -> CompletableFuture.runAsync(() -> map.add(i))).join();
      // then
      assertThat(map).containsExactlyInAnyOrderElementsOf(elements);
    }

    @Test
    public void shouldStopAndReturnErrorIfAFutureFails() {
      // given
      final var map = new ConcurrentHashMap<>();
      // when
      final var future =
          FuturesUtil.parTraverse(
              List.of(1, 2, 3, 4, 5),
              i ->
                  i % 2 == 0
                      ? CompletableFuture.failedFuture(new RuntimeException("Expected"))
                      : CompletableFuture.runAsync(() -> map.put(i, i)));
      // then
      assertThat(future)
          .failsWithin(Duration.ofSeconds(5))
          .withThrowableThat()
          .withMessageContaining("Expected");
      // contains all odd numbers, because the all computation have been spawned immediately
      assertThat(map.keySet()).containsExactly(1, 3, 5);
    }
  }
}
