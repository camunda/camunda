/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class ActorTaskQueueTest {

  @Test
  void shouldBeEmptyInitially() {
    // given
    final var queue = new ActorTaskQueue();

    // then
    assertThat(queue.isEmpty()).isTrue();
    assertThat(queue.peek()).isNull();
    assertThat(queue.pop()).isNull();
    assertThat(queue.trySteal()).isNull();
  }

  @Test
  void shouldPopTasksInAppendOrder() {
    // given
    final var queue = new ActorTaskQueue();
    final var first = newTask();
    final var second = newTask();
    final var third = newTask();

    // when
    queue.append(first);
    queue.append(second);
    queue.append(third);

    // then
    assertThat(queue.pop()).isSameAs(first);
    assertThat(queue.pop()).isSameAs(second);
    assertThat(queue.pop()).isSameAs(third);
    assertThat(queue.pop()).isNull();
  }

  @Test
  void shouldBeEmptyAfterPoppingAllTasks() {
    // given
    final var queue = new ActorTaskQueue();
    queue.append(newTask());
    queue.append(newTask());

    // when
    queue.pop();
    queue.pop();

    // then
    assertThat(queue.isEmpty()).isTrue();
    assertThat(queue.peek()).isNull();
  }

  @Test
  void shouldAcceptNewTasksAfterBeingDrained() {
    // given
    final var queue = new ActorTaskQueue();
    queue.append(newTask());
    queue.pop();
    final var task = newTask();

    // when
    queue.append(task);

    // then
    assertThat(queue.isEmpty()).isFalse();
    assertThat(queue.pop()).isSameAs(task);
    assertThat(queue.isEmpty()).isTrue();
  }

  @Test
  void shouldPeekAtHeadWithoutRemovingIt() {
    // given
    final var queue = new ActorTaskQueue();
    final var first = newTask();
    queue.append(first);
    queue.append(newTask());

    // when
    final var peeked = queue.peek();

    // then
    assertThat(peeked).isNotNull();
    assertThat(peeked.task).isSameAs(first);
    assertThat(queue.pop()).isSameAs(first);
  }

  @Test
  void shouldNotBeEmptyAfterAppend() {
    // given
    final var queue = new ActorTaskQueue();

    // when
    queue.append(newTask());

    // then
    assertThat(queue.isEmpty()).isFalse();
  }

  @Test
  void shouldStealMostRecentlyAppendedTask() {
    // given
    final var queue = new ActorTaskQueue();
    final var first = newTask();
    final var second = newTask();
    queue.append(first);
    queue.append(second);

    // when
    final var stolen = queue.trySteal();

    // then
    assertThat(stolen).isSameAs(second);
  }

  @Test
  void shouldNotPopStolenTask() {
    // given
    final var queue = new ActorTaskQueue();
    final var first = newTask();
    final var second = newTask();
    queue.append(first);
    queue.append(second);

    // when
    final var stolen = queue.trySteal();

    // then
    assertThat(stolen).isSameAs(second);
    assertThat(queue.pop()).isSameAs(first);
    assertThat(queue.pop()).isNull();
  }

  @Test
  void shouldStealOlderTaskWhenNewerOneIsAlreadyClaimed() {
    // given
    final var queue = new ActorTaskQueue();
    final var first = newTask();
    final var second = newTask();
    queue.append(first);
    queue.append(second);
    claim(second);

    // when
    final var stolen = queue.trySteal();

    // then
    assertThat(stolen).isSameAs(first);
    assertThat(queue.trySteal()).isNull();
  }

  @Test
  void shouldSkipTasksClaimedAfterAppend() {
    // given
    final var queue = new ActorTaskQueue();
    final var claimed = newTask();
    final var unclaimed = newTask();
    queue.append(claimed);
    queue.append(unclaimed);
    claim(claimed);

    // when
    final var popped = queue.pop();

    // then
    assertThat(popped).isSameAs(unclaimed);
    assertThat(queue.pop()).isNull();
  }

  @Test
  void shouldPopSameTaskAgainWhenReappendedAfterBeingClaimed() {
    // given
    final var queue = new ActorTaskQueue();
    final var task = newTask();
    queue.append(task);
    claim(task);

    // when - the stale node is still queued, a fresh node carries the new state count
    queue.append(task);

    // then
    assertThat(queue.pop()).isSameAs(task);
    assertThat(queue.pop()).isNull();
  }

  @Test
  void shouldKeepTaskAppendedConcurrentlyWhileRemovingLastNode() {
    // given - an append that lands after poll() decided it removes the last node (tail == next),
    // but before it resets the tail; this is the interleaving where casTail must fail
    final var first = newTask();
    final var concurrentlyAppended = newTask();
    final var hookFired = new AtomicBoolean();
    final var queue =
        new ActorTaskQueue(
            q -> {
              if (hookFired.compareAndSet(false, true)) {
                q.append(concurrentlyAppended);
              }
            });
    queue.append(first);

    // when
    final var popped = queue.pop();

    // then
    assertThat(hookFired).isTrue();
    assertThat(popped).isSameAs(first);
    assertThat(queue.isEmpty()).isFalse();
    assertThat(queue.peek()).isNotNull();
    assertThat(queue.peek().task).isSameAs(concurrentlyAppended);
    assertThat(queue.pop()).isSameAs(concurrentlyAppended);
    assertThat(queue.isEmpty()).isTrue();
  }

  @Test
  void shouldKeepAcceptingTasksAfterConcurrentAppendWhileRemovingLastNode() {
    // given
    final var concurrentlyAppended = newTask();
    final var hookFired = new AtomicBoolean();
    final var queue =
        new ActorTaskQueue(
            q -> {
              if (hookFired.compareAndSet(false, true)) {
                q.append(concurrentlyAppended);
              }
            });
    queue.append(newTask());
    queue.pop();
    final var appendedLater = newTask();

    // when
    queue.append(appendedLater);

    // then
    assertThat(queue.pop()).isSameAs(concurrentlyAppended);
    assertThat(queue.pop()).isSameAs(appendedLater);
    assertThat(queue.pop()).isNull();
  }

  private static ActorTask newTask() {
    return new ActorTask(new Actor() {});
  }

  private static void claim(final ActorTask task) {
    assertThat(task.claim(task.getStateCount())).isTrue();
  }
}
