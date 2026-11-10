/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.mailbox;

import io.camunda.zeebe.mailbox.AdaptiveBackoff;
import io.camunda.zeebe.mailbox.MailboxCleaner;
import io.camunda.zeebe.mailbox.PollLoop;
import io.camunda.zeebe.mailbox.Scheduler;
import io.camunda.zeebe.mailbox.api.MailboxConfig;
import io.camunda.zeebe.mailbox.api.MailboxStore;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Removes the messages and responses of the mailbox that nobody collected in time. The cleanup is
 * global, so one node in the cluster is enough. The leader of the deployment partition runs it, and
 * stops when it stops leading.
 *
 * <p>The loop runs on a thread of its own, because the store may block on I/O.
 */
public final class MailboxCleanupLoop implements AutoCloseable {

  private static final Duration MIN_INTERVAL = Duration.ofSeconds(1);
  private static final Duration MAX_INTERVAL = Duration.ofMinutes(1);

  private final ScheduledExecutorService executor;
  private final PollLoop loop;

  public MailboxCleanupLoop(
      final String name, final MailboxStore store, final MailboxConfig config) {
    executor =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              final var thread = new Thread(runnable, name);
              thread.setDaemon(true);
              return thread;
            });
    final var cleaner = new MailboxCleaner(store, config);
    final var interval = intervalFor(config);
    loop =
        new PollLoop(
            name,
            Scheduler.of(executor),
            new AdaptiveBackoff(interval, interval),
            () -> {
              cleaner.cleanOnce();
              return false;
            });
  }

  /** A tenth of the shorter time to live, so rows do not outlive it by much, within limits. */
  static Duration intervalFor(final MailboxConfig config) {
    final var tenth =
        config.inboxTtl().compareTo(config.outboxTtl()) < 0
            ? config.inboxTtl().dividedBy(10)
            : config.outboxTtl().dividedBy(10);
    if (tenth.compareTo(MIN_INTERVAL) < 0) {
      return MIN_INTERVAL;
    }
    return tenth.compareTo(MAX_INTERVAL) > 0 ? MAX_INTERVAL : tenth;
  }

  public void start() {
    loop.start();
  }

  @Override
  public void close() {
    loop.close();
    executor.shutdownNow();
  }
}
