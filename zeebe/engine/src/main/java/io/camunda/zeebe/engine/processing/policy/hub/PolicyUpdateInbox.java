/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy.hub;

import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Hands one policy update over from the fan-out client's thread to the scheduler, and its outcome
 * back. The fan-out client gives one event at a time and waits for its outcome, so the inbox holds
 * at most one update. It is safe to use from any thread.
 */
@NullMarked
final class PolicyUpdateInbox {

  private final AtomicReference<@Nullable PendingUpdate> pending = new AtomicReference<>();

  /**
   * Hands an update over to the scheduler.
   *
   * @return the outcome of the update; it fails if another update is still pending, or if the
   *     scheduler gives the update up
   */
  CompletableFuture<PolicyUpdateOutcome> offer(
      final long seqNo, final List<PolicyUpdateRecord> chunks) {
    final var update =
        new PendingUpdate(
            seqNo, chunks.getFirst().getUpdateId(), chunks, new CompletableFuture<>());
    if (!pending.compareAndSet(null, update)) {
      return CompletableFuture.failedFuture(
          new IllegalStateException(
              "Expected no policy update to be pending when update '%s' is received, but one is"
                  .formatted(update.updateId())));
    }
    return update.outcome();
  }

  /** The pending update, or {@code null} if there is none. */
  @Nullable PendingUpdate peek() {
    return pending.get();
  }

  /** Completes the update with its outcome, unless it is no longer the pending one. */
  void complete(final PendingUpdate update, final PolicyUpdateOutcome outcome) {
    if (pending.compareAndSet(update, null)) {
      update.outcome().complete(outcome);
    }
  }

  /**
   * Gives the update up, unless it is no longer the pending one. The fan-out client sends it again
   * later.
   */
  void giveUp(final PendingUpdate update, final Throwable cause) {
    if (pending.compareAndSet(update, null)) {
      update.outcome().completeExceptionally(cause);
    }
  }

  /** Gives the pending update up, if there is one. */
  void giveUpPending(final Throwable cause) {
    final var update = pending.getAndSet(null);
    if (update != null) {
      update.outcome().completeExceptionally(cause);
    }
  }

  /**
   * A policy update that waits for its outcome.
   *
   * @param seqNo the Hub sequence of the update
   * @param updateId the ID of the update
   * @param chunks the chunks of the update in index order
   * @param outcome completes with the outcome, once it is committed in the log
   */
  record PendingUpdate(
      long seqNo,
      String updateId,
      List<PolicyUpdateRecord> chunks,
      CompletableFuture<PolicyUpdateOutcome> outcome) {}
}
