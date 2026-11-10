/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.partitionapi;

import io.camunda.zeebe.broker.mailbox.MailboxDrainLoop;
import io.camunda.zeebe.mailbox.api.MailboxConfig;
import io.camunda.zeebe.mailbox.api.MailboxStore;
import io.camunda.zeebe.mailbox.api.MessageKind;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Takes the commands that other partitions left in the inbox of this partition, and writes them to
 * the log. The leader of the partition runs one. Close it when the node stops leading.
 *
 * <p>It hands each command to the receiver of the partition and waits, so the commands enter the
 * log in the order of the inbox and after the commands that arrived over the network before.
 */
public final class InterPartitionMailboxReceiver implements AutoCloseable {

  private static final Duration WRITE_TIMEOUT = Duration.ofSeconds(10);
  private static final Set<MessageKind> KINDS = Set.of(MessageKind.INTER_PARTITION_COMMAND);

  private final MailboxDrainLoop loop;

  /**
   * @param claimerId identifies this node in the inbox, for example its broker id
   */
  public InterPartitionMailboxReceiver(
      final InterPartitionCommandReceiverActor receiver,
      final MailboxStore store,
      final int partitionId,
      final String claimerId,
      final MailboxConfig config) {
    loop =
        new MailboxDrainLoop(
            "mailbox-receiver-" + partitionId,
            store,
            partitionId,
            claimerId(claimerId),
            KINDS,
            config,
            new MailboxInboxWriter(receiver, WRITE_TIMEOUT));
  }

  InterPartitionMailboxReceiver(
      final MailboxCommandHandler handler,
      final MailboxStore store,
      final int partitionId,
      final String claimerId,
      final MailboxConfig config,
      final ScheduledExecutorService executor) {
    loop =
        new MailboxDrainLoop(
            "mailbox-receiver-" + partitionId,
            store,
            partitionId,
            claimerId(claimerId),
            KINDS,
            config,
            new MailboxInboxWriter(handler, WRITE_TIMEOUT),
            executor);
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
  }

  /** Each consumer of the inbox needs its own claimer id, as claims are released for an id. */
  private static String claimerId(final String nodeId) {
    return nodeId + "/inter-partition";
  }
}
