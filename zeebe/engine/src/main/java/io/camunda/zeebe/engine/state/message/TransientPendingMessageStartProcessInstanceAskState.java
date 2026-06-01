/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.message;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Transient (in-memory only) state that tracks when each pending cross-partition message-start ask
 * was last sent. Used by {@link DbMessageStartProcessInstanceAskState} to implement retry
 * scheduling.
 *
 * <p>On recovery the CF is walked and all entries are added with {@code lastSentTime = 0} so they
 * are immediately eligible for re-send. This is safe because the success-only dedup on {@code P_B}
 * bounds the storm.
 *
 * <p>This class is thread-safe. It is intended that one thread mutates (add/update/remove) while
 * another observes via {@link #entriesBefore(long)}.
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
   * Removes a pending ask. Called when any of the three reply intents is applied on {@code P_K}.
   *
   * @param key the key identifying the ask
   */
  public void remove(final PendingAskKey key) {
    pending.remove(key);
  }

  /**
   * Returns all pending asks whose last-sent timestamp is before the given deadline, ordered by
   * that timestamp (oldest first). The returned iterable may include entries that were updated or
   * removed concurrently; the caller must tolerate stale data.
   *
   * @param deadline epoch millis; entries with {@code lastSentTime < deadline} are returned
   * @return an iterable of pending ask keys
   */
  public Iterable<PendingAskKey> entriesBefore(final long deadline) {
    return pending.entrySet().stream()
        .sorted(Map.Entry.comparingByValue())
        .takeWhile(entry -> entry.getValue() < deadline)
        .map(Entry::getKey)
        .collect(Collectors.toList());
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
