/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

/**
 * One frozen commit cut of a partition: the task's state delta, produced output and consumed offset
 * captured at a single barrier by {@link Task#freezeCut(long)}, detached from the live working
 * state so the partition keeps processing while the cut is made durable in the background.
 *
 * <p><b>Lifecycle and threading.</b> The runtime drives one cut at a time per partition through
 * three phases: {@link #publish()} then {@link #persist()} on an IO thread (which exclusively owns
 * the frozen data), then {@link #complete(boolean)} back on the partition's processing thread. The
 * processing thread never touches the frozen data between freeze and completion, and the IO thread
 * never touches the live working state — that separation, not locking, is what makes the
 * concurrency safe.
 *
 * <p><b>Consistency.</b> Everything in the cut was captured at the same barrier as the offset
 * passed to {@link Task#freezeCut(long)}: the persisted state describes exactly the records up to
 * that offset, no more, no less. Records folded after the barrier belong to the next cut; on a
 * crash they are replayed from this cut's offset.
 */
public interface CommitCut {

  /**
   * Produce-before-commit: make the cut's produced output durable at its destination (e.g. publish
   * sealed shuffle deltas, flush serving rows). Runs on the IO thread <em>outside</em> the state
   * transaction; the writes must be idempotent, since a crash before the offset advances replays
   * them. Default no-op for cuts without produced output.
   */
  default void publish() {}

  /**
   * The self-contained durable write of the cut: the frozen state delta and the barrier's offset in
   * one atomic transaction the task owns.
   */
  void persist();

  /**
   * Completes the cut on the partition's processing thread. On success the frozen data is retired —
   * it is durable now and must not be re-persisted. On failure it is merged back underneath the
   * live working state (newer writes win), so the next freeze re-includes it and the cut is retried
   * as part of a larger one.
   */
  void complete(boolean success);
}
