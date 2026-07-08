/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.streaming.aggregate.WindowedCellState.CheckpointDelta;
import io.camunda.eventbridge.streaming.window.Windowed;
import org.junit.jupiter.api.Test;

/**
 * Pins the detach/merge-back contract of the checkpoint delta: a detached cut persists
 * asynchronously while the state tracks new changes, and a failed cut merges back with
 * current-state-wins union semantics.
 */
final class WindowedCellStateTest {

  private final WindowedCellState<String, Long> state = new WindowedCellState<>();

  @Test
  void shouldTrackChangesFreshlyAfterDetachingTheDelta() {
    // given one changed and one evicted cell
    final Windowed<String> a = cell("a");
    final Windowed<String> b = cell("b");
    change(a, 1L);
    change(b, 2L);
    evict(b);

    // when the delta is detached and a new cell changes afterwards
    final CheckpointDelta<String> detached = state.detachCheckpointDelta();
    change(cell("c"), 3L);

    // then the detached cut holds the pre-detach delta and the live tracking only the new change
    assertThat(detached.changed()).containsExactly(a);
    assertThat(detached.evicted()).containsExactly(b);
    final CheckpointDelta<String> next = state.detachCheckpointDelta();
    assertThat(next.changed()).containsExactly(cell("c"));
    assertThat(next.evicted()).isEmpty();
  }

  @Test
  void shouldFillGapsWhenMergingADetachedDeltaBack() {
    // given a detached cut and new activity after the detach
    change(cell("a"), 1L);
    change(cell("b"), 2L);
    evict(cell("b"));
    final CheckpointDelta<String> detached = state.detachCheckpointDelta();
    change(cell("c"), 3L);

    // when the cut failed to persist and merges back
    state.mergeBackCheckpointDelta(detached);

    // then the next cut re-includes the union of both
    final CheckpointDelta<String> next = state.detachCheckpointDelta();
    assertThat(next.changed()).containsExactlyInAnyOrder(cell("a"), cell("c"));
    assertThat(next.evicted()).containsExactly(cell("b"));
  }

  @Test
  void shouldNotResurrectACellEvictedAfterTheDetach() {
    // given a changed cell detached, then evicted after the detach
    final Windowed<String> a = cell("a");
    change(a, 1L);
    final CheckpointDelta<String> detached = state.detachCheckpointDelta();
    evict(a);

    // when the detached cut merges back
    state.mergeBackCheckpointDelta(detached);

    // then the current eviction wins — the next cut deletes the cell, it does not re-write it
    final CheckpointDelta<String> next = state.detachCheckpointDelta();
    assertThat(next.changed()).isEmpty();
    assertThat(next.evicted()).containsExactly(a);
  }

  @Test
  void shouldNotReEvictACellRecreatedAfterTheDetach() {
    // given an evicted cell detached, then re-created after the detach
    final Windowed<String> a = cell("a");
    change(a, 1L);
    evict(a);
    final CheckpointDelta<String> detached = state.detachCheckpointDelta();
    change(a, 2L);

    // when the detached cut merges back
    state.mergeBackCheckpointDelta(detached);

    // then the re-creation wins — the next cut writes the cell, it does not delete it
    final CheckpointDelta<String> next = state.detachCheckpointDelta();
    assertThat(next.changed()).containsExactly(a);
    assertThat(next.evicted()).isEmpty();
  }

  private static Windowed<String> cell(final String key) {
    return new Windowed<>(key, 0L);
  }

  private void change(final Windowed<String> cell, final long value) {
    state.put(cell, value);
    state.index(cell, 1_000L);
    state.markChanged(cell);
  }

  private void evict(final Windowed<String> cell) {
    state.evictDue(1_000L, (windowEnd, due, value) -> due.equals(cell));
  }
}
