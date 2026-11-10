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
import io.camunda.zeebe.protocol.impl.encoding.ErrorResponse;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.transport.ServerOutput;
import io.camunda.zeebe.transport.ServerResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
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
 * time out. A request that stays unanswered for longer than the time to live is forgotten.
 *
 * <p>Thread-safe. The engine writes responses from the stream processor, the handler from its
 * actor.
 */
final class MailboxResponseRouter implements ServerOutput {

  /**
   * The ids of the transport are snowflake ids, which stay below this bit until about 2040. The ids
   * of the mailbox have it set, so the two cannot be mixed up.
   */
  static final long FIRST_REQUEST_ID = 1L << 62;

  /**
   * A mailbox id is {@code FIRST_REQUEST_ID | term << COUNTER_BITS | counter}. The leader of a new
   * term replays the commands that the old leader wrote but did not process, and the engine answers
   * them with the old leader's ids. Since each term has one leader, ids of different terms never
   * match, and such an answer cannot reach the gateway of another request. The term takes the 30
   * bits between the counter and {@code FIRST_REQUEST_ID}; the POC ignores the wrap after 2^30
   * terms and 2^32 requests in one term.
   */
  static final int COUNTER_BITS = 32;

  private static final long TERM_MASK = (1L << (62 - COUNTER_BITS)) - 1;
  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;
  private static final long PURGE_INTERVAL_NANOS = Duration.ofSeconds(1).toNanos();

  private final ServerOutput transport;
  private final MailboxStore store;
  private final Executor executor;
  private final Map<Long, Origin> pending = new ConcurrentHashMap<>();
  private final AtomicLong nextRequestId = new AtomicLong(firstRequestIdOf(0));
  private final Set<Long> notLeading = ConcurrentHashMap.newKeySet();
  private final long ttlNanos;
  private final LongSupplier nanoClock;
  private final AtomicLong lastPurge;

  /**
   * @param transport sends the responses to requests that did not come from the mailbox
   * @param executor runs the inserts into the outbox. The store may block, and the response is sent
   *     from the stream processor, which must not.
   */
  MailboxResponseRouter(
      final ServerOutput transport, final MailboxStore store, final Executor executor) {
    this(transport, store, executor, Duration.ofMinutes(10), System::nanoTime);
  }

  /**
   * @param ttl how long the router waits for the response to a request. After that the engine has
   *     not answered, for example because the command was dropped, and the gateway has given up.
   *     The router forgets the request, so the memory does not grow.
   * @param nanoClock a monotonic clock in nanoseconds
   */
  MailboxResponseRouter(
      final ServerOutput transport,
      final MailboxStore store,
      final Executor executor,
      final Duration ttl,
      final LongSupplier nanoClock) {
    this.transport = transport;
    this.store = store;
    this.executor = executor;
    ttlNanos = ttl.toNanos();
    this.nanoClock = nanoClock;
    lastPurge = new AtomicLong(nanoClock.getAsLong());
  }

  /** The first id that the leader of the term hands out. */
  static long firstRequestIdOf(final long term) {
    return FIRST_REQUEST_ID | (term & TERM_MASK) << COUNTER_BITS;
  }

  /**
   * Starts to hand out the ids of the term in which this node leads the partition. Called when the
   * node becomes leader, before it takes requests from the inbox.
   */
  void startTerm(final long term) {
    clear();
    nextRequestId.set(firstRequestIdOf(term));
  }

  /**
   * Remembers where the response to a request from the mailbox must go.
   *
   * @param gateway the gateway that left the request in the inbox
   * @param gatewayRequestId the id that the gateway gave the request
   * @return the request id to use for the request in the broker
   */
  long register(final GatewayId gateway, final long gatewayRequestId) {
    purgeExpired();
    final var requestId = nextRequestId.getAndIncrement();
    pending.put(requestId, new Origin(gateway, gatewayRequestId, nanoClock.getAsLong()));
    return requestId;
  }

  /**
   * Tells whether the request was turned away because this node does not lead the partition, and
   * forgets that. Such a request must stay in the inbox, because the leader has not handled it.
   */
  boolean takeNotLeading(final long requestId) {
    return notLeading.remove(requestId);
  }

  private static boolean isLeaderMismatch(final byte[] bytes) {
    try {
      final var buffer = new UnsafeBuffer(bytes);
      final var error = new ErrorResponse();
      if (!error.tryWrap(buffer)) {
        return false;
      }
      error.wrap(buffer, 0, buffer.capacity());
      return error.getErrorCode() == ErrorCode.PARTITION_LEADER_MISMATCH;
    } catch (final RuntimeException e) {
      // not an error response, for example a response that is too short to have a header
      return false;
    }
  }

  /** Forgets a request, for example because it could not be handled. */
  void forget(final long requestId) {
    pending.remove(requestId);
  }

  /** Forgets all requests. Their responses are lost, as when the node stops leading. */
  void clear() {
    pending.clear();
    notLeading.clear();
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
    if (isLeaderMismatch(bytes)) {
      // this node does not lead the partition, so the request has not been handled. It is not
      // answered, and stays in the inbox for the node that does lead.
      notLeading.add(response.getRequestId());
      return;
    }
    executor.execute(() -> putResponse(origin, bytes));
  }

  /** Forgets the requests that waited for longer than the time to live. At most once a second. */
  private void purgeExpired() {
    final var now = nanoClock.getAsLong();
    final var last = lastPurge.get();
    if (now - last < PURGE_INTERVAL_NANOS || !lastPurge.compareAndSet(last, now)) {
      return;
    }
    pending.values().removeIf(origin -> now - origin.registeredNanos() > ttlNanos);
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

  private record Origin(GatewayId gateway, long gatewayRequestId, long registeredNanos) {}
}
