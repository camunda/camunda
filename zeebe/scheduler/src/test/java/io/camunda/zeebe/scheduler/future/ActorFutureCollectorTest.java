/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ActorFutureCollectorTest {

  private TestConcurrencyControl concurrencyControl;
  private ActorFuture<String> future1;
  private ActorFuture<String> future2;

  private ActorFuture<List<String>> aggregatedFuture;

  @BeforeEach
  public void setUp() {
    concurrencyControl = new TestConcurrencyControl();

    future1 = concurrencyControl.createFuture();
    future2 = concurrencyControl.createFuture();

    aggregatedFuture =
        Stream.of(future1, future2).collect(new ActorFutureCollector<>(concurrencyControl));
  }

  @Test
  public void shouldAggregateResults() {
    // when
    future1.complete("result1");
    future2.complete("result2");

    // then
    assertThat(aggregatedFuture.join()).containsExactly("result1", "result2");
  }

  @Test
  public void shouldAggregateFuturesThatAreAlreadyCompleted() {
    // given - the test concurrency control runs callbacks of completed futures right away
    final List<ActorFuture<String>> completedFutures =
        Stream.of("result1", "result2", "result3")
            .map(
                result -> {
                  final ActorFuture<String> future = concurrencyControl.createFuture();
                  future.complete(result);
                  return future;
                })
            .toList();

    // when
    final var aggregated =
        completedFutures.stream().collect(new ActorFutureCollector<>(concurrencyControl));

    // then
    assertThat(aggregated.isDone()).isTrue();
    assertThat(aggregated.join()).containsExactly("result1", "result2", "result3");
  }

  @Test
  public void shouldCompleteWithException() {
    // given
    final var testException = new Exception("future2 threw exception");

    // when
    future1.complete("result1");
    future2.completeExceptionally(testException);

    // then
    assertThatThrownBy(() -> aggregatedFuture.join())
        .getCause()
        .hasSuppressedException(testException);
  }

  @Test
  public void shouldAggregateExceptions() {
    // given
    final var testException1 = new Exception("future1 threw exception");
    final var testException2 = new Exception("future2 threw exception");

    // when
    future1.completeExceptionally(testException1);
    future2.completeExceptionally(testException2);

    // then
    assertThatThrownBy(() -> aggregatedFuture.join())
        .getCause()
        .hasSuppressedException(testException1)
        .hasSuppressedException(testException2);
  }
}
