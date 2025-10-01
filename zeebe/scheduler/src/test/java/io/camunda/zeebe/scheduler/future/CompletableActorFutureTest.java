/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.future;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class CompletableActorFutureTest {

  @AutoClose private static final ExecutorService EXECUTOR = Executors.newWorkStealingPool();

  @Nested
  class TraverseIgnoring {
    @Test
    public void shouldReturnCompletedFutureWhenCollectionIsEmpty() {
      assertThat(
              CompletableActorFuture.traverseIgnoring(
                  List.of(),
                  a ->
                      CompletableActorFuture.<Void>completedExceptionally(
                          new RuntimeException("Expected")),
                  EXECUTOR))
          .isDone();
    }

    @Test
    public void shouldRunAllFuturesSequentially() {
      // given
      final var count = 1000;
      final var list = new ArrayBlockingQueue<>(count);
      final var elements = IntStream.range(0, count).boxed().toList();
      // when
      CompletableActorFuture.traverseIgnoring(
              elements,
              i -> {
                list.add(i);
                return CompletableActorFuture.completed();
              },
              EXECUTOR)
          .join();
      // then
      assertThat(list).containsExactlyElementsOf(elements);
    }

    @Test
    public void shouldStopAndReturnErrorIfAFutureFails() {
      // given
      final var map = new ConcurrentHashMap<>();
      // when
      final var future =
          CompletableActorFuture.traverseIgnoring(
              List.of(1, 2, 3),
              i -> {
                if (i % 2 == 0) {
                  return CompletableActorFuture.completedExceptionally(
                      new RuntimeException("Expected"));
                }
                map.put(i, i);
                return CompletableActorFuture.completed();
              },
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
}
