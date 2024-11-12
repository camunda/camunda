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
 * This class is used by {@link DbProcessMessageSubscriptionState} and {@link
 * DbMessageSubscriptionState} to keep track of pending (process) message subscriptions. {@link
 * PendingSubscription PendingSubscriptions} are added with a last sent time. The time can be
 * updated with {@link #update(PendingSubscription, long)}. Pending subscriptions are retrieved with
 * {@link #entriesBefore(long)}, ordered by the last sent time. <br>
 * This class is thread safe. It's intended use is that one thread is adding, removing and updating
 * entries while another thread is "observing" the entries by calling {@link #entriesBefore(long)}.
 * <br>
 * The iterable returned by {@link #entriesBefore(long)} may return outdated entries (i.e. entries
 * which last sent time was updated so that those entries should not have been returned) and already
 * removed entries. For the intended use, this is not a problem.
 */
public final class TransientPendingSubscriptionState {

  // Reconsider thread-safety implications when changing the map implementation.
  private final Map<PendingSubscription, Long> pending = new ConcurrentHashMap<>();

  public void add(final PendingSubscription pendingSubscription, final long lastSentTime) {
    pending.put(pendingSubscription, lastSentTime);
  }

  public void update(final PendingSubscription pendingSubscription, final long lastSentTime) {
    pending.put(pendingSubscription, lastSentTime);
  }

  public void remove(final PendingSubscription pendingSubscription) {
    pending.remove(pendingSubscription);
  }

  Iterable<PendingSubscription> entriesBefore(final long deadline) {
    return pending.entrySet().stream()
        .sorted(Map.Entry.comparingByValue())
        .takeWhile(entry -> entry.getValue() < deadline)
        .map(Entry::getKey)
        .collect(Collectors.toList());
  }

  public record PendingSubscription(long elementInstanceKey, String messageName, String tenantId) {}
}
