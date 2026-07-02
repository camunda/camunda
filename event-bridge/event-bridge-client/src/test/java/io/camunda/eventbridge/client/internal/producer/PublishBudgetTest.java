/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client.internal.producer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/** Verifies the in-flight-byte backpressure of {@link PublishBudget}. */
final class PublishBudgetTest {

  @Test
  void shouldGrantImmediatelyWhenUnderCap() {
    // given
    final PublishBudget budget = new PublishBudget(100);

    // when
    final CompletableFuture<Void> granted = budget.acquire(60);

    // then
    assertThat(granted).isCompleted();
  }

  @Test
  void shouldParkAcquireUntilAnEarlierSendReleases() {
    // given: the cap is fully reserved
    final PublishBudget budget = new PublishBudget(100);
    budget.acquire(100).join();

    // when: a second acquire cannot fit
    final CompletableFuture<Void> parked = budget.acquire(40);
    assertThat(parked).isNotDone();

    // then: releasing enough admits it, in order
    budget.release(100);
    assertThat(parked).isCompleted();
  }

  @Test
  void shouldAdmitAnOverCapBatchOnceNothingElseIsInFlight() {
    // given: a batch larger than the whole cap
    final PublishBudget budget = new PublishBudget(100);

    // when
    final CompletableFuture<Void> granted = budget.acquire(250);

    // then: it is admitted rather than deadlocking, since the pool is fully free
    assertThat(granted).isCompleted();
  }

  @Test
  void shouldPreserveFifoOrderSoALargeHeadHoldsBackSmallerWaiters() {
    // given: cap fully reserved, then a large waiter queued ahead of a small one
    final PublishBudget budget = new PublishBudget(100);
    budget.acquire(100).join();
    final CompletableFuture<Void> big = budget.acquire(80);
    final CompletableFuture<Void> small = budget.acquire(10);

    // when: 50 is released — enough for the small one, but the big one is ahead in FIFO order
    budget.release(50);

    // then: neither is admitted yet (the head doesn't fit, so the queue is held)
    assertThat(big).isNotDone();
    assertThat(small).isNotDone();

    // when: the rest is released
    budget.release(50);

    // then: the big head is admitted, and the small one follows
    assertThat(big).isCompleted();
    assertThat(small).isCompleted();
  }

  @Test
  void shouldFailParkedAcquiresOnClose() {
    // given: a parked acquire
    final PublishBudget budget = new PublishBudget(100);
    budget.acquire(100).join();
    final CompletableFuture<Void> parked = budget.acquire(40);

    // when
    budget.close();

    // then
    assertThat(parked).isCompletedExceptionally();
  }
}
