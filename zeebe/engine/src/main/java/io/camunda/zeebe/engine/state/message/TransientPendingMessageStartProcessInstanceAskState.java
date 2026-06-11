/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.message;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transient (in-memory only) state that tracks when each pending cross-partition message-start ask
 * was last sent. Used by {@link DbMessageStartProcessInstanceAskState} to implement retry
 * scheduling.
 *
 * <p>On recovery the CF is walked and each entry is seeded with a last-sent timestamp (see {@link
 * DbMessageStartProcessInstanceAskState#onRecovered}): fresh asks with {@code 0} (immediately
 * eligible, preserving at-least-once first delivery), already-backed-off asks with the recovery
 * time (so they resume at their back-off cadence instead of all re-probing at once).
 *
 * <p>This class is thread-safe. It is intended that one thread mutates (add/update/remove) while
 * another observes via {@link #entries()}.
 */
public final class TransientPendingMessageStartProcessInstanceAskState {

  private final Map<PendingAskKey, Long> pending = new ConcurrentHashMap<>();

  /**
   * Adds a pending ask with the given last-sent timestamp.
   *
   * @param key the key identifying the ask
   * @param lastSentTime epoch millis when the ask was last sent (0 means "eligible immediately")
   */
  public void add(final PendingAskKey key, final long lastSentTime) {
    pending.put(key, lastSentTime);
  }

  /**
   * Updates the last-sent timestamp for an existing pending ask.
   *
   * @param key the key identifying the ask
   * @param lastSentTime epoch millis when the ask was last sent
   */
  public void update(final PendingAskKey key, final long lastSentTime) {
    pending.put(key, lastSentTime);
  }

  /**
   * Removes a pending ask. Called when the ask succeeds or the buffered message expires.
   *
   * @param key the key identifying the ask
   */
  public void remove(final PendingAskKey key) {
    pending.remove(key);
  }

  /**
   * Returns a snapshot of all pending asks with their last-sent timestamps. The retry scheduler
   * joins each entry with its persisted ask (for the rejection count and the re-send payload) to
   * decide per-ask eligibility. A snapshot is returned so the caller may mutate the underlying
   * state (e.g. {@link #update}) while iterating.
   *
   * @return a snapshot of {@code (key, lastSentTime)} entries
   */
  public Iterable<Entry<PendingAskKey, Long>> entries() {
    return List.copyOf(pending.entrySet());
  }

  /** Returns {@code true} if there are no pending asks. */
  public boolean isEmpty() {
    return pending.isEmpty();
  }

  /**
   * Key identifying a pending cross-partition message-start ask.
   *
   * @param messageKey the key of the message that triggered the ask
   * @param processDefinitionKey the key of the process definition the ask targets
   */
  public record PendingAskKey(long messageKey, long processDefinitionKey) {}
}
