/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy.hub;

import io.camunda.zeebe.engine.processing.policy.PolicyUpdateOutcomeListener;
import io.camunda.zeebe.engine.processing.policy.hub.PolicyUpdateOutcome.Applied;
import io.camunda.zeebe.engine.processing.policy.hub.PolicyUpdateOutcome.Rejected;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.NullMarked;

/**
 * Keeps the outcomes of policy updates that are committed in the log of partition 1, until the
 * scheduler reports them. The processors add them from post-commit tasks on the stream processor's
 * actor; the scheduler reads them on its own actor.
 *
 * <p>Only these outcomes, and the state read when processing starts, are durable. The scheduler
 * never reports an update as applied from a later state read, because that state may hold records
 * that are not committed in the log yet.
 */
@NullMarked
final class PolicyUpdateOutcomes implements PolicyUpdateOutcomeListener {

  /** Enough for the outcomes of the updates a previous leader left open, which nobody takes. */
  private static final int MAX_OUTCOMES = 16;

  private final AtomicLong committedSeqNo = new AtomicLong();

  private final Map<String, PolicyUpdateOutcome> outcomes =
      new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(final Entry<String, PolicyUpdateOutcome> eldest) {
          return size() > MAX_OUTCOMES;
        }
      };

  @Override
  public void onCommitted(final String updateId, final long seqNo) {
    committedSeqNo.accumulateAndGet(seqNo, Math::max);
    put(updateId, new Applied());
  }

  @Override
  public void onFailed(final String updateId, final String reason) {
    put(updateId, new Rejected(reason));
  }

  /**
   * Remembers the Hub sequence of the policy that was committed when processing started. Processing
   * starts on the state of the committed log, so the sequence is durable.
   */
  void onProcessingStarted(final long recoveredSeqNo) {
    committedSeqNo.accumulateAndGet(recoveredSeqNo, Math::max);
  }

  /** The highest Hub sequence that is known to be committed in the log. */
  long committedSeqNo() {
    return committedSeqNo.get();
  }

  /** Takes the outcome of the update, if it is committed in the log. */
  synchronized Optional<PolicyUpdateOutcome> take(final String updateId) {
    return Optional.ofNullable(outcomes.remove(updateId));
  }

  private synchronized void put(final String updateId, final PolicyUpdateOutcome outcome) {
    outcomes.put(updateId, outcome);
  }
}
