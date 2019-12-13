/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.cluster.messaging.MessagingException;
import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.ClientRequest;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class AtomixClientOutputAdapter extends Actor implements ClientOutput {

  private static final Duration RETRY_DELAY = Duration.ofMillis(10);

  private final ClusterCommunicationService communicationService;

  public AtomixClientOutputAdapter(final ClusterCommunicationService communicationService) {
    this.communicationService = communicationService;
  }

  @Override
  public ActorFuture<DirectBuffer> sendRequestWithRetry(
      final Supplier<Integer> nodeIdSupplier,
      final Predicate<DirectBuffer> responseValidator,
      final ClientRequest clientRequest,
      final Duration timeout) {

    // copy once
    final var length = clientRequest.getLength();
    final var requestBytes = new byte[length];
    final var buffer = new UnsafeBuffer(requestBytes);
    clientRequest.write(buffer, 0);

    final var partitionId = clientRequest.getPartitionId();

    final var requestFuture = new CompletableActorFuture<DirectBuffer>();
    final var requestContext =
        new RequestContext(
            requestFuture, nodeIdSupplier, partitionId, requestBytes, responseValidator, timeout);
    actor.call(
        () -> {
          actor.runDelayed(timeout, () -> timeoutFuture(requestContext));
          tryToSend(requestContext);
        });

    return requestFuture;
  }

  private void tryToSend(final RequestContext requestContext) {
    if (requestContext.isDone()) {
      return;
    }

    final var nodeId = requestContext.getNodeId();
    if (nodeId == null) {
      actor.runDelayed(RETRY_DELAY, () -> tryToSend(requestContext));
      return;
    }

    final var requestBytes = requestContext.getRequestBytes();
    communicationService
        .<byte[], byte[]>send(
            requestContext.getTopicName(),
            requestBytes,
            MemberId.from(nodeId.toString()),
            requestContext.calculateTimeout())
        .whenComplete(
            (response, errorOnRequest) -> {
              final var currentFuture = requestContext.getCurrentFuture();
              if (errorOnRequest == null) {
                final var responseBuffer = new UnsafeBuffer(response);
                if (requestContext.verifyResponse(responseBuffer)) {
                  currentFuture.complete(responseBuffer);
                } else {
                  // no valid response - retry in respect of the timeout
                  actor.runDelayed(RETRY_DELAY, () -> tryToSend(requestContext));
                }
              } else {
                // normally the root exception is a completion exception
                // and the cause is either connect or non remote handler
                final var cause = errorOnRequest.getCause();
                if (cause instanceof ConnectException
                    || cause instanceof MessagingException.NoRemoteHandler) {
                  // no registered subscription yet
                  actor.runDelayed(RETRY_DELAY, () -> tryToSend(requestContext));
                } else {
                  currentFuture.completeExceptionally(errorOnRequest);
                }
              }
            });
  }

  private void timeoutFuture(final RequestContext requestContext) {
    if (requestContext.isDone()) {
      return;
    }

    final var currentFuture = requestContext.getCurrentFuture();
    final var timeout = requestContext.getTimeout();
    currentFuture.completeExceptionally(
        new TimeoutException("Request timed out after " + timeout.toString()));
  }
}
