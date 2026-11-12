/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.lincheck.datastructures.ModelCheckingOptions;
import org.jetbrains.lincheck.datastructures.Operation;
import org.jetbrains.lincheck.datastructures.Validate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * Model checks {@link ActorTaskQueue} with Lincheck: it generates scenarios of the operations
 * below, runs each under many thread interleavings, and calls {@link #validate()} at the end of
 * each run. A failure is reported with the smallest scenario that still fails and the interleaving.
 *
 * <p>The queue is not linearizable: a steal may find nothing although a task is waiting, for
 * example when the newest node is stale and its append is still in progress. So the operations
 * return nothing, and {@link #validate()} checks what must hold once all threads are done.
 *
 * <p>Thieves unlink runs of a single stale node already, so that they write to shared nodes as
 * often as possible in scenarios small enough for a model checker. Lincheck explores interleavings
 * under sequential consistency; it does not cover reorderings of a weaker memory model.
 *
 * <p>Classes used here, like {@link ActorTask}, must not be mocked with Mockito elsewhere in this
 * module: its inline mock maker rewrites the mocked class for the whole JVM, and Lincheck then
 * fails while restoring its memory snapshot between interleavings.
 */
// Lincheck instruments classes in the whole JVM while it runs
@Isolated
public final class ActorTaskQueueLincheckTest {
  private final ActorTaskQueue queue = new ActorTaskQueue(1);
  private final AtomicInteger appended = new AtomicInteger();
  private final AtomicInteger claimed = new AtomicInteger();

  @Operation
  public void append() {
    queue.append(new ActorTask(null));
    appended.incrementAndGet();
  }

  @Operation
  public void steal() {
    if (queue.trySteal() != null) {
      claimed.incrementAndGet();
    }
  }

  /** What an actor thread does with a stolen task that it schedules again. */
  @Operation
  public void stealAndAppend() {
    final ActorTask task = queue.trySteal();
    if (task != null) {
      claimed.incrementAndGet();
      queue.append(task);
      appended.incrementAndGet();
    }
  }

  /** Only the thread that owns the queue pops. */
  @Operation(nonParallelGroup = "owner")
  public void pop() {
    if (queue.pop() != null) {
      claimed.incrementAndGet();
    }
  }

  /**
   * Once all threads are done, a thief must find every task that is still waiting: if the owner can
   * pop one afterwards, the {@code prev} chain was hiding it. And every append of a waiting task
   * was claimed exactly once.
   */
  @Validate
  public void validate() {
    int stolen = 0;
    while (queue.trySteal() != null) {
      stolen++;
    }
    int popped = 0;
    while (queue.pop() != null) {
      popped++;
    }
    if (popped != 0) {
      throw new AssertionError(
          "Expected thieves to find every waiting task, but the owner popped " + popped + " more");
    }
    if (claimed.get() + stolen != appended.get()) {
      throw new AssertionError(
          "Expected every one of %d appended waiting tasks to be claimed once, but %d were claimed"
              .formatted(appended.get(), claimed.get() + stolen));
    }
  }

  @Test
  void shouldFindEveryWaitingTask() {
    new ModelCheckingOptions()
        .iterations(5)
        .invocationsPerIteration(500)
        .threads(3)
        .actorsPerThread(2)
        .actorsBefore(4)
        .actorsAfter(0)
        .check(ActorTaskQueueLincheckTest.class);
  }

  @Tag("randomized")
  @Test
  void shouldFindEveryWaitingTaskInLargerScenarios() {
    new ModelCheckingOptions()
        .iterations(30)
        .invocationsPerIteration(2_000)
        .threads(3)
        .actorsPerThread(3)
        .actorsBefore(5)
        .actorsAfter(0)
        .check(ActorTaskQueueLincheckTest.class);
  }
}
