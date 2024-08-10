/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.cluster.messaging.MessagingException.NoRemoteHandler;
import io.atomix.cluster.messaging.MessagingException.NoSuchMemberException;
import io.atomix.cluster.messaging.MessagingException.RemoteHandlerFailure;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.transport.stream.impl.messages.MessageUtil;
import io.camunda.zeebe.transport.stream.impl.messages.StreamTopics;
import io.camunda.zeebe.util.ExponentialBackoff;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.LongUnaryOperator;
import org.agrona.collections.ArrayUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side actor which takes care of the network communication between the remote stream clients
 * (e.g. gateways) and servers (e.g. brokers). Sets up handlers for shared topics to receive add,
 * remove, and remove all requests, and manages sending restart requests to added clients.
 *
 * @param <M> type of the stream's metadata
 */
public final class RemoteStreamTransport<M> extends Actor {
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
  private static final Logger LOG = LoggerFactory.getLogger(RemoteStreamTransport.class);
  private static final int INITIAL_RETRY_DELAY_MS = 100;

  private final ClusterCommunicationService transport;
  private final RemoteStreamApiHandler<M> requestHandler;
  private final LongUnaryOperator retryDelaySupplier;

  public RemoteStreamTransport(
      final ClusterCommunicationService transport, final RemoteStreamApiHandler<M> requestHandler) {
    this(transport, requestHandler, new ExponentialBackoff());
  }

  @VisibleForTesting
  RemoteStreamTransport(
      final ClusterCommunicationService transport,
      final RemoteStreamApiHandler<M> requestHandler,
      final LongUnaryOperator retryDelaySupplier) {
    this.transport = transport;
    this.requestHandler = requestHandler;
    this.retryDelaySupplier = retryDelaySupplier;
  }

  @Override
  protected void onActorStarting() {
    transport.replyTo(
        StreamTopics.ADD.topic(),
        MessageUtil::parseAddRequest,
        requestHandler::add,
        BufferUtil::bufferAsArray,
        actor::run);
    transport.replyTo(
        StreamTopics.REMOVE.topic(),
        MessageUtil::parseRemoveRequest,
        requestHandler::remove,
        BufferUtil::bufferAsArray,
        actor::run);
    transport.replyTo(
        StreamTopics.REMOVE_ALL.topic(),
        Function.identity(),
        this::onRemoveAll,
        Function.identity(),
        actor::run);
  }

  @Override
  protected void onActorClosing() {
    transport.unsubscribe(StreamTopics.ADD.topic());
    transport.unsubscribe(StreamTopics.REMOVE.topic());
    transport.unsubscribe(StreamTopics.REMOVE_ALL.topic());
    requestHandler.close();
  }

  public void removeAll(final MemberId member) {
    actor.run(() -> requestHandler.removeAll(member));
  }

  private byte[] onRemoveAll(final MemberId sender, final byte[] ignored) {
    requestHandler.removeAll(sender);
    return ArrayUtil.EMPTY_BYTE_ARRAY;
  }

  public CompletableFuture<Void> restartStreams(final MemberId receiver) {
    final var completed = new CompletableFuture<Void>();
    sendRestartStreamsRequest(receiver, completed, INITIAL_RETRY_DELAY_MS);
    return completed;
  }

  private void sendRestartStreamsRequest(
      final MemberId receiver, final CompletableFuture<Void> completed, final long retryDelayMs) {
    try {
      sendRestartStreamsRequest(receiver)
          .whenCompleteAsync(
              (ok, error) -> onRestartStreamsResponse(receiver, error, completed, retryDelayMs),
              actor);
    } catch (final Exception e) {
      LOG.warn("Failed to restart streams for member '{}'", receiver, e);
    }
  }

  private CompletableFuture<Void> sendRestartStreamsRequest(final MemberId receiver) {
    return transport
        .send(
            StreamTopics.RESTART_STREAMS.topic(),
            ArrayUtil.EMPTY_BYTE_ARRAY,
            Function.identity(),
            Function.identity(),
            receiver,
            REQUEST_TIMEOUT)
        .thenApply(ok -> null);
  }

  private void onRestartStreamsResponse(
      final MemberId receiver,
      final Throwable error,
      final CompletableFuture<Void> completed,
      final long retryDelayMs) {
    if (error == null) {
      LOG.debug("Requested streams from client service member '{}'", receiver);
      completed.complete(null);
      return;
    }

    final var cause = error.getCause();
    switch (cause) {
      // it's possible that the member that was just added has since been removed in between
      // retries;
      // if this is the case, it'll be re-added eventually
      case final NoSuchMemberException e -> {
        LOG.trace(
            """
            Failed to restart streams for member '{}', which has been removed from the
            membership protocol; can be safely ignored.""",
            receiver,
            e);
        completed.complete(null);
      }
      // this error means the remote member is not handling requests of this type; either it's not
      // a gateway, or it's still starting up or shutting down. in either case, restarting streams
      // makes no sense
      case final NoRemoteHandler e -> {
        LOG.trace(
            """
            Failed to restart streams for member '{}'; either it's not a client
            stream service, or it's still starting up. Can be safely ignored.""",
            receiver,
            e);
        completed.complete(null);
      }
      // no point retrying if the remote handler failed to handle our request
      case final RemoteHandlerFailure e -> {
        LOG.warn(
            """
            Failed to restart streams for member '{}'; unrecoverable error occurred on recipient
            side, will not retry.""",
            receiver,
            e);
        completed.completeExceptionally(e);
      }
      default -> {
        LOG.warn(
            "Failed to restart streams for member '{}', retrying in {}ms",
            receiver,
            retryDelayMs,
            error);
        final var nextRetryDelay = retryDelaySupplier.applyAsLong(retryDelayMs);
        actor.schedule(
            Duration.ofMillis(nextRetryDelay),
            () -> sendRestartStreamsRequest(receiver, completed, nextRetryDelay));
      }
    }
  }
}
