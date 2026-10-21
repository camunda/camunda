/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import io.camunda.zeebe.eventplane.notification.UploadNotice;
import io.camunda.zeebe.eventplane.segment.LogId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The service's only state: the newest upload notice of each log, and the open notification
 * streams. A restart loses both, which costs nothing that matters: workers reconnect and list the
 * bucket, and the group view lists it for the logs that have no notice.
 */
final class NoticeBoard implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(NoticeBoard.class);

  private final Map<LogId, UploadNotice> newest = new ConcurrentHashMap<>();
  private final Set<Subscription> subscriptions = ConcurrentHashMap.newKeySet();
  private volatile boolean closed;

  /** Remembers the notice if it is the newest of its log by last index, and passes it on. */
  void publish(final UploadNotice notice) {
    final var log = notice.log();
    newest.merge(
        log, notice, (old, latest) -> latest.lastIndex() >= old.lastIndex() ? latest : old);
    for (final var subscription : subscriptions) {
      if (subscription.subscribedTo(log) && !subscription.offer(notice)) {
        LOG.debug("Dropped the notice of {} for a subscriber that doesn't keep up", log);
      }
    }
  }

  /** The newest notice of the log, by last index, if there has been one since the start. */
  Optional<UploadNotice> newest(final LogId log) {
    return Optional.ofNullable(newest.get(log));
  }

  /** Opens a subscription to the logs. The caller ends it with {@link #unsubscribe}. */
  Subscription subscribe(final Set<LogId> logs) {
    final var subscription = new Subscription(logs);
    subscriptions.add(subscription);
    if (closed) {
      unsubscribe(subscription);
    }
    return subscription;
  }

  void unsubscribe(final Subscription subscription) {
    subscriptions.remove(subscription);
    subscription.close();
  }

  /** The number of open subscriptions. */
  int subscribers() {
    return subscriptions.size();
  }

  /** Ends every subscription, so that the threads that write them stop. */
  @Override
  public void close() {
    closed = true;
    for (final var subscription : subscriptions) {
      unsubscribe(subscription);
    }
  }
}
