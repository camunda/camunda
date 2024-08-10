/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;

final class AsyncAggregatingSubscriberTest {

  @Test
  void shouldCompleteOnlyAfterAllFuturesComplete() {
    // given
    final var aggregator = new AsyncAggregatingSubscriber<Integer>(16);
    final var completed = CompletableFuture.completedFuture(1);
    final var delayed = new CompletableFuture<Integer>();

    aggregator.onSubscribe(mock(Subscription.class));
    final CompletableFuture<Collection<Integer>> result = aggregator.result();

    // when
    aggregator.onNext(completed);
    aggregator.onNext(delayed);
    aggregator.onComplete();

    // then
    assertThat(result).isNotDone();

    delayed.complete(2);
    Awaitility.await().until(result::isDone);
    assertThat(result.join()).containsExactlyInAnyOrder(1, 2);
  }

  @Test
  void shouldFailIfOneFutureFails() {
    // given
    final var aggregator = new AsyncAggregatingSubscriber<Integer>(16);
    final var completed = CompletableFuture.completedFuture(1);
    final var failed = new CompletableFuture<Integer>();

    aggregator.onSubscribe(mock(Subscription.class));
    final CompletableFuture<Collection<Integer>> result = aggregator.result();

    // when
    aggregator.onNext(completed);
    aggregator.onNext(failed);
    aggregator.onComplete();

    // then
    failed.completeExceptionally(new RuntimeException("Failed"));

    Awaitility.await()
        .untilAsserted(
            () ->
                assertThat(result)
                    .failsWithin(Duration.ofMillis(100))
                    .withThrowableOfType(ExecutionException.class)
                    .withMessageContaining("Failed"));
  }

  @Test
  void shouldFailsIfSubscriptionFails() {
    // given
    final var aggregator = new AsyncAggregatingSubscriber<Integer>(16);
    final var completed = CompletableFuture.completedFuture(1);
    final var failed = new CompletableFuture<Integer>();

    aggregator.onSubscribe(mock(Subscription.class));
    final CompletableFuture<Collection<Integer>> result = aggregator.result();

    // when
    aggregator.onNext(completed);
    aggregator.onNext(failed);
    aggregator.onError(new RuntimeException("Failed"));

    // then
    Awaitility.await()
        .untilAsserted(
            () ->
                assertThat(result)
                    .failsWithin(Duration.ofMillis(100))
                    .withThrowableOfType(ExecutionException.class)
                    .withMessageContaining("Failed"));
  }
}
