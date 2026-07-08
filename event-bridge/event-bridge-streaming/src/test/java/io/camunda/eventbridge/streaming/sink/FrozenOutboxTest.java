/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.sink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class FrozenOutboxTest {

  private final FrozenOutbox<String> outbox = new FrozenOutbox<>();
  private final List<String> drained = new ArrayList<>();

  @Test
  void shouldDrainFrozenItemsInStagingOrder() {
    // given
    outbox.stage("a");
    outbox.stage("b");
    outbox.stage("c");

    // when
    outbox.freeze();
    final int count = outbox.drainFrozen(drained::add);

    // then
    assertThat(count).isEqualTo(3);
    assertThat(drained).containsExactly("a", "b", "c");
  }

  @Test
  void shouldReemitFailedCutAheadOfNewerItems() {
    // given a cut that fails after draining
    outbox.stage("old-1");
    outbox.stage("old-2");
    outbox.freeze();
    outbox.drainFrozen(item -> {});
    outbox.completeFrozen(false);

    // ... and newer items staged before the next cut
    outbox.stage("new-1");

    // when the next cut freezes and drains
    outbox.freeze();
    outbox.drainFrozen(drained::add);

    // then the failed cut's items come first, in their original order
    assertThat(drained).containsExactly("old-1", "old-2", "new-1");
  }

  @Test
  void shouldEmitEachItemExactlyOnceAcrossFailRetrySuccess() {
    // given a first cut that fails
    outbox.stage("a");
    outbox.freeze();
    outbox.drainFrozen(drained::add);
    outbox.completeFrozen(false);

    // when the retry cut succeeds and a third cut follows
    outbox.stage("b");
    outbox.freeze();
    outbox.drainFrozen(drained::add);
    outbox.completeFrozen(true);

    outbox.stage("c");
    outbox.freeze();
    outbox.drainFrozen(drained::add);
    outbox.completeFrozen(true);

    // then the failed cut's item is re-emitted exactly once and never again after success
    assertThat(drained).containsExactly("a", "a", "b", "c");
  }

  @Test
  void shouldNotExposeItemsStagedAfterFreezeToTheFrozenDrain() {
    // given a frozen cut
    outbox.stage("frozen-item");
    outbox.freeze();

    // when the owner keeps staging while the IO thread drains
    outbox.stage("next-cut-item");
    outbox.drainFrozen(drained::add);
    outbox.completeFrozen(true);

    // then the frozen drain saw only the items staged before the barrier
    assertThat(drained).containsExactly("frozen-item");

    // ... and the later item belongs to the next cut
    drained.clear();
    outbox.freeze();
    outbox.drainFrozen(drained::add);
    assertThat(drained).containsExactly("next-cut-item");
  }

  @Test
  void shouldRejectFreezeWhileAFrozenPileIsOutstanding() {
    // given
    outbox.freeze();

    // when / then
    assertThatIllegalStateException()
        .isThrownBy(outbox::freeze)
        .withMessageContaining("freeze() was called again");
  }

  @Test
  void shouldRejectDrainWithoutAFrozenPile() {
    assertThatIllegalStateException()
        .isThrownBy(() -> outbox.drainFrozen(drained::add))
        .withMessageContaining("frozen pile to drain");
  }

  @Test
  void shouldRejectCompleteWithoutAFrozenPile() {
    assertThatIllegalStateException()
        .isThrownBy(() -> outbox.completeFrozen(true))
        .withMessageContaining("frozen pile to complete");
  }

  @Test
  void shouldRejectSynchronousDrainWhileAFrozenPileIsOutstanding() {
    // given
    outbox.freeze();

    // when / then
    assertThatIllegalStateException()
        .isThrownBy(() -> outbox.drainPending(drained::add))
        .withMessageContaining("frozen pile is outstanding");
  }

  @Test
  void shouldReportStagedAndFrozenState() {
    // given an empty outbox
    assertThat(outbox.hasStaged()).isFalse();
    assertThat(outbox.hasFrozen()).isFalse();

    // when an item is staged
    outbox.stage("a");

    // then it is staged but nothing is frozen
    assertThat(outbox.hasStaged()).isTrue();
    assertThat(outbox.hasFrozen()).isFalse();

    // when the pile is frozen
    outbox.freeze();

    // then the active pile is fresh and a frozen pile is outstanding
    assertThat(outbox.hasStaged()).isFalse();
    assertThat(outbox.hasFrozen()).isTrue();

    // when the cut fails
    outbox.completeFrozen(false);

    // then the retained items count as staged for the next cut
    assertThat(outbox.hasStaged()).isTrue();
    assertThat(outbox.hasFrozen()).isFalse();

    // when the retry cut succeeds
    outbox.freeze();
    outbox.completeFrozen(true);

    // then nothing is left
    assertThat(outbox.hasStaged()).isFalse();
    assertThat(outbox.hasFrozen()).isFalse();
  }

  @Test
  void shouldDrainRetainedItemsBeforeStagedOnTheSynchronousBoundary() {
    // given a failed cut's retained items and newer staged ones
    outbox.stage("old");
    outbox.freeze();
    outbox.completeFrozen(false);
    outbox.stage("new");

    // when
    outbox.drainPending(drained::add);

    // then retained items re-emit first and everything is cleared
    assertThat(drained).containsExactly("old", "new");
    assertThat(outbox.hasStaged()).isFalse();
  }

  @Test
  void shouldReportZeroDrainedForAnEmptyFrozenPile() {
    // given
    outbox.freeze();

    // when
    final int count = outbox.drainFrozen(drained::add);

    // then the caller can skip its durable flush
    assertThat(count).isZero();
    assertThat(drained).isEmpty();
  }
}
