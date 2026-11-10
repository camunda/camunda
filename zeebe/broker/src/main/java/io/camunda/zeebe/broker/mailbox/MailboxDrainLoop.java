/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.mailbox;

import io.camunda.zeebe.mailbox.AdaptiveBackoff;
import io.camunda.zeebe.mailbox.InboxDrainer;
import io.camunda.zeebe.mailbox.InboxMessageWriter;
import io.camunda.zeebe.mailbox.PollLoop;
import io.camunda.zeebe.mailbox.Scheduler;
import io.camunda.zeebe.mailbox.api.MailboxConfig;
import io.camunda.zeebe.mailbox.api.MailboxStore;
import io.camunda.zeebe.mailbox.api.MessageKind;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Takes the messages of some kinds out of the inbox of a partition and hands them to a writer. The
 * leader of the partition runs one for each consumer of the inbox. Close it when the node stops
 * leading.
 *
 * <p>The loop runs on a thread of its own, because the store may block on I/O. The writer may block
 * too.
 */
public final class MailboxDrainLoop implements AutoCloseable {

  private final ScheduledExecutorService executor;
  private final InboxDrainer drainer;
  private final PollLoop loop;

  /**
   * Creates a loop that runs on a new daemon thread.
   *
   * @param claimerId identifies this consumer in the inbox. Consumers that run side by side on one
   *     inbox need different ids, because the claims are released for an id.
   */
  public MailboxDrainLoop(
      final String name,
      final MailboxStore store,
      final int partitionId,
      final String claimerId,
      final Set<MessageKind> kinds,
      final MailboxConfig config,
      final InboxMessageWriter writer) {
    this(
        name,
        store,
        partitionId,
        claimerId,
        kinds,
        config,
        writer,
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              final var thread = new Thread(runnable, name);
              thread.setDaemon(true);
              return thread;
            }));
  }

  /** Creates a loop that runs on the given executor, which must have one thread. */
  public MailboxDrainLoop(
      final String name,
      final MailboxStore store,
      final int partitionId,
      final String claimerId,
      final Set<MessageKind> kinds,
      final MailboxConfig config,
      final InboxMessageWriter writer,
      final ScheduledExecutorService executor) {
    this.executor = executor;
    drainer = new InboxDrainer(store, partitionId, claimerId, kinds, config, writer);
    loop =
        new PollLoop(
            name,
            Scheduler.of(executor),
            new AdaptiveBackoff(config.minPollInterval(), config.maxPollInterval()),
            () -> {
              final var result = drainer.drainOnce();
              // Progress means the next poll comes soon. A store failure or a writer that takes
              // nothing counts as idle, so the loop backs off instead of hammering.
              return result.written() > 0 && !result.failed();
            });
  }

  public void start() {
    loop.start();
  }

  /**
   * Stops the loop and gives the claims of this consumer back, so that the next leader does not
   * wait for them to time out. Does not block: the release runs on the thread of the loop.
   */
  @Override
  public void close() {
    loop.close();
    executor.execute(drainer::releaseClaims);
    executor.shutdown();
  }
}
