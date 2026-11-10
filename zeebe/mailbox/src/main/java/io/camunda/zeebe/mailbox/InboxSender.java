/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox;

import io.camunda.zeebe.mailbox.api.MailboxStore;
import io.camunda.zeebe.mailbox.api.MailboxStoreException;
import io.camunda.zeebe.mailbox.api.MessageKind;
import io.camunda.zeebe.mailbox.api.NewInboxMessage;
import java.time.Instant;
import java.time.InstantSource;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Puts messages into the inbox of a partition. The insert runs on the given executor, because the
 * store may block on I/O and the caller, for example a stream processor, must not.
 *
 * <p>A failed insert is reported through the returned future and is not retried. The callers of the
 * network path have the same behaviour: they send once, and the engine repeats a command if it gets
 * no answer.
 *
 * <p>Thread-safe.
 */
public final class InboxSender {

  private static final Logger LOG = LoggerFactory.getLogger(InboxSender.class);

  private final MailboxStore store;
  private final String senderId;
  private final Executor executor;
  private final AtomicLong nextRequestId;

  /**
   * @param senderId identifies this sender in the inbox, for example a gateway id or a broker id
   * @param executor runs the inserts
   * @param clock used once, to start the request ids after every id of an earlier run
   */
  public InboxSender(
      final MailboxStore store,
      final String senderId,
      final Executor executor,
      final InstantSource clock) {
    this.store = store;
    this.senderId = senderId;
    this.executor = executor;
    nextRequestId = new AtomicLong(ChronoUnit.MICROS.between(Instant.EPOCH, clock.instant()));
  }

  /**
   * Returns a request id that no earlier message of this sender has, also not before a restart of
   * the process, as long as the clock does not go back.
   */
  public long nextRequestId() {
    return nextRequestId.incrementAndGet();
  }

  /**
   * @param partitionId the partition that must process the message
   * @param kind the kind of the message
   * @param requestId the id from {@link #nextRequestId()}
   * @param payload the serialized message
   * @return a future that completes with true if the message is in the inbox, and with false if the
   *     insert failed
   */
  public CompletableFuture<Boolean> send(
      final int partitionId, final MessageKind kind, final long requestId, final byte[] payload) {
    final var result = new CompletableFuture<Boolean>();
    try {
      executor.execute(() -> result.complete(insert(partitionId, kind, requestId, payload)));
    } catch (final RuntimeException e) {
      LOG.warn("Could not schedule the insert into the inbox of partition {}", partitionId, e);
      result.complete(false);
    }
    return result;
  }

  private boolean insert(
      final int partitionId, final MessageKind kind, final long requestId, final byte[] payload) {
    try {
      store.insert(new NewInboxMessage(partitionId, kind, senderId, requestId, payload));
      return true;
    } catch (final MailboxStoreException e) {
      LOG.warn("Could not insert a message into the inbox of partition {}", partitionId, e);
      return false;
    }
  }
}
