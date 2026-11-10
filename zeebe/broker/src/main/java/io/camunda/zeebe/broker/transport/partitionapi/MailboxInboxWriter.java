/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.partitionapi;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.mailbox.InboxMessageWriter;
import io.camunda.zeebe.mailbox.api.InboxMessage;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

/**
 * Writes the inter-partition commands of an inbox to the log of the partition, through the receiver
 * of the partition.
 *
 * <p>It handles one message at a time and waits for the result before the next one. If the log does
 * not take a message, no later message is written, so the order of the inbox is kept. The wait
 * blocks the calling thread, so it must not run on an actor thread.
 */
final class MailboxInboxWriter implements InboxMessageWriter {

  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;

  private final MailboxCommandHandler handler;
  private final Duration timeout;

  /**
   * @param timeout how long to wait for one message to be written
   */
  MailboxInboxWriter(final MailboxCommandHandler handler, final Duration timeout) {
    this.handler = handler;
    this.timeout = timeout;
  }

  @Override
  public int writeAll(final List<InboxMessage> messages) {
    var written = 0;
    for (final var message : messages) {
      if (!write(message, written)) {
        break;
      }
      written++;
    }
    return written;
  }

  /**
   * @return true if the command is in the log. False if the log did not take it. If the outcome is
   *     unknown, it throws when nothing was written yet, so that the caller sees the failure.
   *     Otherwise it returns false and keeps what was confirmed.
   */
  private boolean write(final InboxMessage message, final int alreadyWritten) {
    try {
      final var sender = MemberId.from(message.sender());
      final var accepted =
          handler
              .handleMailboxMessage(sender, message.payload())
              .join(timeout.toMillis(), TimeUnit.MILLISECONDS);
      return Boolean.TRUE.equals(accepted);
    } catch (final Exception e) {
      // join throws the failure of the future, also a checked one, without declaring it
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      if (alreadyWritten == 0) {
        throw new IllegalStateException(
            "Could not tell if message %d of partition %d was written"
                .formatted(message.sequence(), message.partitionId()),
            e);
      }
      LOG.warn(
          "Could not tell if message {} of partition {} was written, stopping after {} messages",
          message.sequence(),
          message.partitionId(),
          alreadyWritten,
          e);
      return false;
    }
  }
}
