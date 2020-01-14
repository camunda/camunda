/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl;

import io.atomix.cluster.messaging.MessagingException;
import io.atomix.cluster.messaging.MessagingService;
import io.zeebe.transport.ClientRequest;
import io.zeebe.transport.ClientTransport;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.net.ConnectException;
import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class AtomixClientTransportAdapter extends Actor implements ClientTransport {

  private static final Duration RETRY_DELAY = Duration.ofMillis(10);

  private final MessagingService messagingService;

  public AtomixClientTransportAdapter(final MessagingService messagingService) {
    this.messagingService = messagingService;
  }

  @Override
  public ActorFuture<DirectBuffer> sendRequestWithRetry(
      final Supplier<String> nodeAddressSupplier,
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
            requestFuture,
            nodeAddressSupplier,
            partitionId,
            requestBytes,
            responseValidator,
            timeout);
    actor.call(
        () -> {
          final var scheduledTimer = actor.runDelayed(timeout, () -> timeoutFuture(requestContext));
          requestContext.setScheduledTimer(scheduledTimer);
          tryToSend(requestContext);
        });

    return requestFuture;
  }

  private void tryToSend(final RequestContext requestContext) {
    if (requestContext.isDone()) {
      return;
    }

    final var nodeAddress = requestContext.getNodeAddress();
    if (nodeAddress == null) {
      actor.runDelayed(RETRY_DELAY, () -> tryToSend(requestContext));
      return;
    }

    final var requestBytes = requestContext.getRequestBytes();
    messagingService
        .sendAndReceive(
            nodeAddress,
            requestContext.getTopicName(),
            requestBytes,
            requestContext.calculateTimeout())
        .whenComplete(
            (response, errorOnRequest) ->
                actor.run(() -> handleResponse(requestContext, response, errorOnRequest)));
  }

  private void handleResponse(
      final RequestContext requestContext, final byte[] response, final Throwable errorOnRequest) {
    if (requestContext.isDone()) {
      return;
    }

    if (errorOnRequest == null) {
      final var responseBuffer = new UnsafeBuffer(response);
      if (requestContext.verifyResponse(responseBuffer)) {
        requestContext.complete(responseBuffer);
      } else {
        // no valid response - retry in respect of the timeout
        actor.runDelayed(RETRY_DELAY, () -> tryToSend(requestContext));
      }
    } else {
      // normally the root exception is a completion exception
      // and the cause is either connect or non remote handler
      final var cause = errorOnRequest.getCause();
      if (exceptionShowsConnectionIssue(errorOnRequest) || exceptionShowsConnectionIssue(cause)) {
        // no registered subscription yet
        actor.runDelayed(RETRY_DELAY, () -> tryToSend(requestContext));
      } else {
        requestContext.completeExceptionally(errorOnRequest);
      }
    }
  }

  private boolean exceptionShowsConnectionIssue(Throwable throwable) {
    return throwable instanceof ConnectException
        || throwable instanceof MessagingException.NoRemoteHandler;
  }

  private void timeoutFuture(final RequestContext requestContext) {
    if (requestContext.isDone()) {
      return;
    }

    requestContext.timeout();
  }
}
