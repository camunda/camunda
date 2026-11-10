/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.mailbox.api.GatewayId;
import io.camunda.zeebe.mailbox.api.MailboxStore;
import io.camunda.zeebe.mailbox.api.MailboxStoreException;
import io.camunda.zeebe.mailbox.api.OutboxResponse;
import io.camunda.zeebe.transport.ServerOutput;
import io.camunda.zeebe.transport.ServerResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

/**
 * Sends the response to a request from the mailbox back through the mailbox, and every other
 * response through the transport.
 *
 * <p>A request from the mailbox gets a request id from this router. The command carries it in the
 * log, and so does the response that the engine writes later. When the response comes, the router
 * finds the gateway that sent the request, and puts the response into that gateway's outbox. A
 * response for an id that the router does not know goes to the transport, as it would without a
 * mailbox.
 *
 * <p>The router keeps what it knows in memory, as the transport does with its pending requests. If
 * the node stops leading the partition, the responses that are still due are lost, and the gateways
 * time out.
 *
 * <p>Thread-safe. The engine writes responses from the stream processor, the handler from its
 * actor.
 */
final class MailboxResponseRouter implements ServerOutput {

  /**
   * The ids of the transport start at 0 and count up. The ids of the mailbox start far above, so
   * the two cannot be mixed up.
   */
  static final long FIRST_REQUEST_ID = 1L << 62;

  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;

  private final ServerOutput transport;
  private final MailboxStore store;
  private final Executor executor;
  private final Map<Long, Origin> pending = new ConcurrentHashMap<>();
  private final AtomicLong nextRequestId = new AtomicLong(FIRST_REQUEST_ID);

  /**
   * @param transport sends the responses to requests that did not come from the mailbox
   * @param executor runs the inserts into the outbox. The store may block, and the response is sent
   *     from the stream processor, which must not.
   */
  MailboxResponseRouter(
      final ServerOutput transport, final MailboxStore store, final Executor executor) {
    this.transport = transport;
    this.store = store;
    this.executor = executor;
  }

  /**
   * Remembers where the response to a request from the mailbox must go.
   *
   * @param gateway the gateway that left the request in the inbox
   * @param gatewayRequestId the id that the gateway gave the request
   * @return the request id to use for the request in the broker
   */
  long register(final GatewayId gateway, final long gatewayRequestId) {
    final var requestId = nextRequestId.getAndIncrement();
    pending.put(requestId, new Origin(gateway, gatewayRequestId));
    return requestId;
  }

  /** Forgets a request, for example because it could not be handled. */
  void forget(final long requestId) {
    pending.remove(requestId);
  }

  /** Forgets all requests. Their responses are lost, as when the node stops leading. */
  void clear() {
    pending.clear();
  }

  int pendingCount() {
    return pending.size();
  }

  @Override
  public void sendResponse(final ServerResponse response) {
    final var origin = pending.remove(response.getRequestId());
    if (origin == null) {
      transport.sendResponse(response);
      return;
    }

    // the response object is reused by its writer, so it is copied before the executor runs
    final var bytes = new byte[response.getLength()];
    response.write(new UnsafeBuffer(bytes), 0);
    executor.execute(() -> putResponse(origin, bytes));
  }

  private void putResponse(final Origin origin, final byte[] bytes) {
    try {
      store.putResponse(new OutboxResponse(origin.gateway(), origin.gatewayRequestId(), bytes));
    } catch (final MailboxStoreException e) {
      LOG.warn(
          "Could not put the response to request {} of gateway {} into the mailbox",
          origin.gatewayRequestId(),
          origin.gateway().value(),
          e);
    }
  }

  private record Origin(GatewayId gateway, long gatewayRequestId) {}
}
