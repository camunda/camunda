/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.partitionapi;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.mailbox.InboxSender;
import io.camunda.zeebe.mailbox.api.MessageKind;
import org.slf4j.Logger;

/**
 * The path for an inter-partition command to a partition that the sender cannot reach over the
 * network. The command goes into the inbox of the receiving partition, and the node that leads that
 * partition takes it from there.
 */
public interface InterPartitionMailbox {

  /**
   * Hands the command over without blocking. A failure is logged and not retried, as a command that
   * is sent over the network. The engine repeats a command that stays unanswered.
   *
   * @param receiverPartitionId the partition that must process the command
   * @param message the encoded message, as it would be sent over the network
   */
  void send(int receiverPartitionId, byte[] message);

  /** A mailbox that puts the commands into the inbox with the given sender. */
  static InterPartitionMailbox using(final InboxSender sender) {
    final Logger log = Loggers.TRANSPORT_LOGGER;
    return (receiverPartitionId, message) ->
        sender
            .send(
                receiverPartitionId,
                MessageKind.INTER_PARTITION_COMMAND,
                sender.nextRequestId(),
                message)
            .thenAccept(
                inserted -> {
                  if (!inserted) {
                    log.warn(
                        "Could not put a command for partition {} into the mailbox",
                        receiverPartitionId);
                  }
                });
  }
}
