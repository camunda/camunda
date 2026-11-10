/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl.mailbox;

import io.camunda.zeebe.broker.client.api.mailbox.GatewayMailboxSetup;
import io.camunda.zeebe.mailbox.AdaptiveBackoff;
import io.camunda.zeebe.mailbox.InboxSender;
import io.camunda.zeebe.mailbox.PollLoop;
import io.camunda.zeebe.mailbox.ResponseAwaiter;
import io.camunda.zeebe.mailbox.Scheduler;
import io.camunda.zeebe.mailbox.api.GatewayId;
import io.camunda.zeebe.mailbox.api.MailboxStoreException;
import io.camunda.zeebe.mailbox.api.MessageKind;
import io.camunda.zeebe.mailbox.api.PartitionLeader;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * The way for a gateway to reach a partition leader that it cannot see: it asks the store where the
 * leader is, puts the request into the inbox of the partition, and collects the response from its
 * outbox.
 *
 * <p>A request is never sent again after a timeout, because the leader may have processed it.
 *
 * <p>Thread-safe. Call {@link #start()} before sending and {@link #close()} at the end.
 */
public final class GatewayMailbox implements AutoCloseable {

  private final GatewayMailboxSetup setup;
  private final InboxSender sender;
  private final ResponseAwaiter awaiter;
  private final ScheduledExecutorService pollExecutor;
  private final PollLoop pollLoop;

  public GatewayMailbox(final GatewayMailboxSetup setup) {
    this(setup, InstantSource.system());
  }

  GatewayMailbox(final GatewayMailboxSetup setup, final InstantSource clock) {
    this.setup = setup;
    sender = new InboxSender(setup.store(), setup.gatewayId(), setup.executor(), clock);
    awaiter = new ResponseAwaiter(setup.store(), new GatewayId(setup.gatewayId()), clock);
    pollExecutor =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              final var thread = new Thread(runnable, "gateway-mailbox-responses");
              thread.setDaemon(true);
              return thread;
            });
    pollLoop =
        new PollLoop(
            "gateway-mailbox-responses",
            Scheduler.of(pollExecutor),
            new AdaptiveBackoff(setup.config().minPollInterval(), setup.config().maxPollInterval()),
            () -> awaiter.pollOnce() > 0);
  }

  public void start() {
    pollLoop.start();
  }

  /**
   * Looks up the recorded leader of a partition.
   *
   * @return the leader, or empty if none is recorded or the store cannot be reached
   */
  public CompletableFuture<Optional<PartitionLeader>> findLeader(final int partitionId) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            return setup.store().findLeader(partitionId);
          } catch (final MailboxStoreException e) {
            return Optional.empty();
          }
        },
        setup.executor());
  }

  /**
   * Sends a request to the inbox of a partition and waits for the response.
   *
   * @param payload the serialized request, as it would go over the network
   * @param timeout how long to wait for the response
   * @return the serialized response. Fails with a {@link java.util.concurrent.TimeoutException} if
   *     none comes in time, and with an {@link IllegalStateException} if the request could not be
   *     put into the inbox.
   */
  public CompletableFuture<byte[]> send(
      final int partitionId, final byte[] payload, final Duration timeout) {
    final var requestId = sender.nextRequestId();
    // wait first, so that a fast response cannot arrive before the wait has started
    final var response = awaiter.await(requestId, timeout);
    sender
        .send(partitionId, MessageKind.CLIENT_REQUEST, requestId, payload)
        .whenComplete(
            (inserted, error) -> {
              if (error != null || !Boolean.TRUE.equals(inserted)) {
                response.completeExceptionally(
                    new IllegalStateException(
                        "Could not put the request into the inbox of partition " + partitionId,
                        error));
              }
            });
    return response;
  }

  @Override
  public void close() {
    pollLoop.close();
    pollExecutor.shutdownNow();
  }
}
