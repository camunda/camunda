/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.Iterator;
import org.junit.jupiter.api.Test;

public final class ClosedQueueTest {

  @Test
  void shouldReturnZeroSize() {
    // given
    final ClosedQueue queue = new ClosedQueue();

    // when
    final int size = queue.size();

    // then
    assertThat(size).isZero();
  }

  @Test
  void shouldBeEmpty() {
    // given
    final ClosedQueue queue = new ClosedQueue();

    // when
    final boolean isEmpty = queue.isEmpty();

    // then
    assertThat(isEmpty).isTrue();
  }

  @Test
  void shouldNotContainAnyElement() {
    // given
    final ClosedQueue queue = new ClosedQueue();
    final ActorJob job = mock(ActorJob.class);

    // when
    final boolean contains = queue.contains(job);

    // then
    assertThat(contains).isFalse();
  }

  @Test
  void shouldReturnEmptyIterator() {
    // given
    final ClosedQueue queue = new ClosedQueue();

    // when
    final Iterator<ActorJob> iterator = queue.iterator();

    // then
    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  void shouldReturnEmptyDescendingIterator() {
    // given
    final ClosedQueue queue = new ClosedQueue();

    // when
    final Iterator<ActorJob> iterator = queue.descendingIterator();

    // then
    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  void shouldReturnEmptyObjectArray() {
    // given
    final ClosedQueue queue = new ClosedQueue();

    // when
    final Object[] array = queue.toArray();

    // then
    assertThat(array).isEmpty();
  }

  @Test
  void shouldReturnEmptyTypedArray() {
    // given
    final ClosedQueue queue = new ClosedQueue();
    final ActorJob[] inputArray = new ActorJob[0];

    // when
    final ActorJob[] array = queue.toArray(inputArray);

    // then
    assertThat(array).isEmpty();
  }

  @Test
  void shouldSetFirstElementToNullInTypedArray() {
    // given
    final ClosedQueue queue = new ClosedQueue();
    final ActorJob[] inputArray = new ActorJob[3];
    inputArray[0] = mock(ActorJob.class);
    inputArray[1] = mock(ActorJob.class);
    inputArray[2] = mock(ActorJob.class);

    // when
    final ActorJob[] array = queue.toArray(inputArray);

    // then
    assertThat(array[0]).isNull();
    assertThat(array[1]).isNotNull();
    assertThat(array[2]).isNotNull();
  }

  @Test
  void shouldClearWithoutException() {
    // given
    final ClosedQueue queue = new ClosedQueue();

    // when/then - should not throw
    queue.clear();
  }

  @Test
  void shouldReturnTrueForContainsAllWithEmptyCollection() {
    // given
    final ClosedQueue queue = new ClosedQueue();

    // when
    final boolean containsAll = queue.containsAll(Collections.emptyList());

    // then
    assertThat(containsAll).isTrue();
  }

  @Test
  void shouldReturnFalseForContainsAllWithNonEmptyCollection() {
    // given
    final ClosedQueue queue = new ClosedQueue();
    final ActorJob job = mock(ActorJob.class);

    // when
    final boolean containsAll = queue.containsAll(Collections.singletonList(job));

    // then
    assertThat(containsAll).isFalse();
  }

  @Test
  void shouldFailFutureOnOffer() {
    // given
    final ClosedQueue queue = new ClosedQueue();
    final ActorJob job = mock(ActorJob.class);

    // when
    final boolean offered = queue.offer(job);

    // then
    assertThat(offered).isTrue();
    verify(job).failFuture("Actor is closed");
  }

  @Test
  void shouldThrowUnsupportedOperationForAdd() {
    // given
    final ClosedQueue queue = new ClosedQueue();
    final ActorJob job = mock(ActorJob.class);

    // when/then
    assertThatThrownBy(() -> queue.add(job)).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldThrowUnsupportedOperationForRemove() {
    // given
    final ClosedQueue queue = new ClosedQueue();
    final ActorJob job = mock(ActorJob.class);

    // when/then
    assertThatThrownBy(() -> queue.remove(job)).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldThrowUnsupportedOperationForRemoveHead() {
    // given
    final ClosedQueue queue = new ClosedQueue();

    // when/then
    assertThatThrownBy(() -> queue.remove()).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldThrowUnsupportedOperationForPoll() {
    // given
    final ClosedQueue queue = new ClosedQueue();

    // when/then
    assertThatThrownBy(() -> queue.poll()).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldThrowUnsupportedOperationForPeek() {
    // given
    final ClosedQueue queue = new ClosedQueue();

    // when/then
    assertThatThrownBy(() -> queue.peek()).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldThrowUnsupportedOperationForElement() {
    // given
    final ClosedQueue queue = new ClosedQueue();

    // when/then
    assertThatThrownBy(() -> queue.element()).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldThrowUnsupportedOperationForAddAll() {
    // given
    final ClosedQueue queue = new ClosedQueue();
    final ActorJob job = mock(ActorJob.class);

    // when/then
    assertThatThrownBy(() -> queue.addAll(Collections.singletonList(job)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldThrowUnsupportedOperationForRemoveAll() {
    // given
    final ClosedQueue queue = new ClosedQueue();
    final ActorJob job = mock(ActorJob.class);

    // when/then
    assertThatThrownBy(() -> queue.removeAll(Collections.singletonList(job)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldThrowUnsupportedOperationForRetainAll() {
    // given
    final ClosedQueue queue = new ClosedQueue();
    final ActorJob job = mock(ActorJob.class);

    // when/then
    assertThatThrownBy(() -> queue.retainAll(Collections.singletonList(job)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldThrowUnsupportedOperationForAddFirst() {
    // given
    final ClosedQueue queue = new ClosedQueue();
    final ActorJob job = mock(ActorJob.class);

    // when/then
    assertThatThrownBy(() -> queue.addFirst(job)).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldThrowUnsupportedOperationForAddLast() {
    // given
    final ClosedQueue queue = new ClosedQueue();
    final ActorJob job = mock(ActorJob.class);

    // when/then
    assertThatThrownBy(() -> queue.addLast(job)).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldThrowUnsupportedOperationForOfferFirst() {
    // given
    final ClosedQueue queue = new ClosedQueue();
    final ActorJob job = mock(ActorJob.class);

    // when/then
    assertThatThrownBy(() -> queue.offerFirst(job))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldThrowUnsupportedOperationForOfferLast() {
    // given
    final ClosedQueue queue = new ClosedQueue();
    final ActorJob job = mock(ActorJob.class);

    // when/then
    assertThatThrownBy(() -> queue.offerLast(job))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldThrowUnsupportedOperationForRemoveFirst() {
    // given
    final ClosedQueue queue = new ClosedQueue();

    // when/then
    assertThatThrownBy(() -> queue.removeFirst()).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldThrowUnsupportedOperationForRemoveLast() {
    // given
    final ClosedQueue queue = new ClosedQueue();

    // when/then
    assertThatThrownBy(() -> queue.removeLast()).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldThrowUnsupportedOperationForPollFirst() {
    // given
    final ClosedQueue queue = new ClosedQueue();

    // when/then
    assertThatThrownBy(() -> queue.pollFirst()).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldThrowUnsupportedOperationForPollLast() {
    // given
    final ClosedQueue queue = new ClosedQueue();

    // when/then
    assertThatThrownBy(() -> queue.pollLast()).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldThrowUnsupportedOperationForGetFirst() {
    // given
    final ClosedQueue queue = new ClosedQueue();

    // when/then
    assertThatThrownBy(() -> queue.getFirst()).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldThrowUnsupportedOperationForGetLast() {
    // given
    final ClosedQueue queue = new ClosedQueue();

    // when/then
    assertThatThrownBy(() -> queue.getLast()).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldThrowUnsupportedOperationForPeekFirst() {
    // given
    final ClosedQueue queue = new ClosedQueue();

    // when/then
    assertThatThrownBy(() -> queue.peekFirst()).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldThrowUnsupportedOperationForPeekLast() {
    // given
    final ClosedQueue queue = new ClosedQueue();

    // when/then
    assertThatThrownBy(() -> queue.peekLast()).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldThrowUnsupportedOperationForRemoveFirstOccurrence() {
    // given
    final ClosedQueue queue = new ClosedQueue();
    final ActorJob job = mock(ActorJob.class);

    // when/then
    assertThatThrownBy(() -> queue.removeFirstOccurrence(job))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldThrowUnsupportedOperationForRemoveLastOccurrence() {
    // given
    final ClosedQueue queue = new ClosedQueue();
    final ActorJob job = mock(ActorJob.class);

    // when/then
    assertThatThrownBy(() -> queue.removeLastOccurrence(job))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldThrowUnsupportedOperationForPush() {
    // given
    final ClosedQueue queue = new ClosedQueue();
    final ActorJob job = mock(ActorJob.class);

    // when/then
    assertThatThrownBy(() -> queue.push(job)).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldThrowUnsupportedOperationForPop() {
    // given
    final ClosedQueue queue = new ClosedQueue();

    // when/then
    assertThatThrownBy(() -> queue.pop()).isInstanceOf(UnsupportedOperationException.class);
  }
}
