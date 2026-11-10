/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.partitionapi;

import io.camunda.zeebe.mailbox.AdaptiveBackoff;
import io.camunda.zeebe.mailbox.InboxDrainer;
import io.camunda.zeebe.mailbox.PollLoop;
import io.camunda.zeebe.mailbox.Scheduler;
import io.camunda.zeebe.mailbox.api.MailboxConfig;
import io.camunda.zeebe.mailbox.api.MailboxStore;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Takes the commands that other partitions left in the inbox of this partition, and writes them to
 * the log. The leader of the partition runs one. Close it when the node stops leading.
 *
 * <p>The loop runs on a thread of its own, because the store may block on I/O. It hands each
 * command to the receiver of the partition and waits, so the commands enter the log in the order of
 * the inbox and after the commands that arrived over the network before.
 */
public final class InterPartitionMailboxReceiver implements AutoCloseable {

  private static final Duration WRITE_TIMEOUT = Duration.ofSeconds(10);

  private final ScheduledExecutorService executor;
  private final InboxDrainer drainer;
  private final PollLoop loop;

  /**
   * @param claimerId identifies this node in the inbox, for example its broker id
   */
  public InterPartitionMailboxReceiver(
      final InterPartitionCommandReceiverActor receiver,
      final MailboxStore store,
      final int partitionId,
      final String claimerId,
      final MailboxConfig config) {
    this(
        receiver,
        store,
        partitionId,
        claimerId,
        config,
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              final var thread = new Thread(runnable, "mailbox-receiver-" + partitionId);
              thread.setDaemon(true);
              return thread;
            }));
  }

  InterPartitionMailboxReceiver(
      final MailboxCommandHandler handler,
      final MailboxStore store,
      final int partitionId,
      final String claimerId,
      final MailboxConfig config,
      final ScheduledExecutorService executor) {
    this.executor = executor;
    drainer =
        new InboxDrainer(
            store, partitionId, claimerId, config, new MailboxInboxWriter(handler, WRITE_TIMEOUT));
    loop =
        new PollLoop(
            "mailbox-receiver-" + partitionId,
            Scheduler.of(executor),
            new AdaptiveBackoff(config.minPollInterval(), config.maxPollInterval()),
            () -> {
              final var result = drainer.drainOnce();
              // Progress means the next poll comes soon. A store failure or a log that takes
              // nothing counts as idle, so the loop backs off instead of hammering.
              return result.written() > 0 && !result.failed();
            });
  }

  public void start() {
    loop.start();
  }

  /**
   * Stops the loop and gives the claims of this node back, so that the next leader does not wait
   * for them to time out. Does not block: the release runs on the thread of the loop.
   */
  @Override
  public void close() {
    loop.close();
    executor.execute(drainer::releaseClaims);
    executor.shutdown();
  }
}
