/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.mailbox.InboxMessageWriter;
import io.camunda.zeebe.mailbox.api.GatewayId;
import io.camunda.zeebe.mailbox.api.InboxMessage;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

/**
 * Handles the requests that gateways left in the inbox of a partition, as the request handler
 * handles a request from the network. The response goes back through the {@link
 * MailboxResponseRouter}.
 *
 * <p>A message counts as written when the request is handled, that is, when its command is in the
 * log, or when an error response was written. In both cases the gateway has an answer or will get
 * one, so the message leaves the inbox. The exception is a request that is turned away because this
 * node does not lead the partition any more. It is not answered and stays in the inbox, because the
 * node that leads has not seen it. It handles one message at a time and waits for the result before
 * the next one, so the order of the inbox is kept. The wait blocks the calling thread, so it must
 * not run on an actor thread.
 */
final class ClientRequestMailboxWriter implements InboxMessageWriter {

  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;

  private final CommandApiRequestHandler handler;
  private final MailboxResponseRouter router;
  private final int partitionNumber;
  private final Duration timeout;

  /**
   * @param partitionNumber the number of the partition whose inbox is drained
   * @param timeout how long to wait for one request to be handled
   */
  ClientRequestMailboxWriter(
      final CommandApiRequestHandler handler,
      final MailboxResponseRouter router,
      final int partitionNumber,
      final Duration timeout) {
    this.handler = handler;
    this.router = router;
    this.partitionNumber = partitionNumber;
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
   * @return true if the request is handled. If the outcome is unknown, it throws when nothing was
   *     written yet, so that the caller sees the failure. Otherwise it returns false and keeps what
   *     was confirmed.
   */
  private boolean write(final InboxMessage message, final int alreadyWritten) {
    final var requestId = router.register(new GatewayId(message.sender()), message.requestId());
    try {
      handler
          .handleRequestFromMailbox(
              router,
              partitionNumber,
              requestId,
              new UnsafeBuffer(message.payload()),
              0,
              message.payload().length)
          .join(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (router.takeNotLeading(requestId)) {
        // the handler turned the request away, because this node stopped leading. The message
        // stays claimed until the claims are released, and the next leader handles it.
        LOG.debug(
            "Request {} of partition {} stays in the inbox, this node does not lead it",
            message.sequence(),
            message.partitionId());
        return false;
      }
      return true;
    } catch (final Exception e) {
      // join throws the failure of the future, also a checked one, without declaring it
      router.forget(requestId);
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      if (alreadyWritten == 0) {
        throw new IllegalStateException(
            "Could not tell if request %d of partition %d was handled"
                .formatted(message.sequence(), message.partitionId()),
            e);
      }
      LOG.warn(
          "Could not tell if request {} of partition {} was handled, stopping after {} requests",
          message.sequence(),
          message.partitionId(),
          alreadyWritten,
          e);
      return false;
    }
  }
}
