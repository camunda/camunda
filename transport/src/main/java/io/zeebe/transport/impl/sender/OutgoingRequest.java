/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl.sender;

import io.zeebe.transport.ClientResponse;
import io.zeebe.transport.Loggers;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.RequestTimeoutException;
import io.zeebe.transport.impl.ClientResponseImpl;
import io.zeebe.transport.impl.IncomingResponse;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;
import java.util.Deque;
import java.util.LinkedList;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public class OutgoingRequest {
  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;

  private final TransportHeaderWriter headerWriter = new TransportHeaderWriter();

  private final ActorFuture<ClientResponse> responseFuture = new CompletableActorFuture<>();

  private final Supplier<RemoteAddress> remoteAddressSupplier;

  private final Predicate<DirectBuffer> retryPredicate;

  private final Duration timeout;

  private final Deque<RemoteAddress> remotesTried = new LinkedList<>();

  private final MutableDirectBuffer requestBuffer;

  private long timerId = -1;

  private long lastRequestId = -1;

  private boolean isTimedout;

  private IncomingResponse lastResponse;

  public OutgoingRequest(
      Supplier<RemoteAddress> remoteAddressSupplier,
      Predicate<DirectBuffer> retryPredicate,
      UnsafeBuffer requestBuffer,
      Duration timeout) {
    this.remoteAddressSupplier = remoteAddressSupplier;
    this.retryPredicate = retryPredicate;
    this.requestBuffer = requestBuffer;
    this.timeout = timeout;
  }

  public ActorFuture<ClientResponse> getResponseFuture() {
    return responseFuture;
  }

  public RemoteAddress getNextRemoteAddress() {
    return remoteAddressSupplier.get();
  }

  public boolean tryComplete(IncomingResponse incomingResponse) {
    final DirectBuffer data = incomingResponse.getResponseBuffer();

    if (responseFuture.isDone()) {
      return true;
    } else if (!retryPredicate.test(data)) {
      completeFuture(incomingResponse);

      return true;
    } else {
      // should retry
      lastResponse = incomingResponse;
      return false;
    }
  }

  private void completeFuture(IncomingResponse incomingResponse) {
    try {
      final RemoteAddress remoteAddress = remotesTried.peekFirst();
      final ClientResponseImpl response = new ClientResponseImpl(incomingResponse, remoteAddress);
      responseFuture.complete(response);
    } catch (Exception e) {
      LOG.debug("Could not complete request future", e);
    }
  }

  public void fail(Throwable throwable) {
    try {
      if (lastResponse != null) {
        LOG.warn("Suppressing throwable", throwable);
        completeFuture(lastResponse);
      } else {
        responseFuture.completeExceptionally(throwable);
      }
    } catch (Exception e) {
      LOG.debug("Could not complete request future exceptionally", e);
    }
  }

  public DirectBuffer getRequestBuffer() {
    return requestBuffer;
  }

  public RemoteAddress getCurrentRemoteAddress() {
    return remotesTried.peekFirst();
  }

  public Duration getTimeout() {
    return timeout;
  }

  public void markRemoteAddress(RemoteAddress remoteAddress) {
    if (!remoteAddress.equals(remotesTried.peekFirst())) {
      remotesTried.push(remoteAddress);
    }
  }

  public TransportHeaderWriter getHeaderWriter() {
    return headerWriter;
  }

  public boolean hasTimeoutScheduled() {
    return timerId != -1;
  }

  public long getTimerId() {
    return timerId;
  }

  public void setTimerId(long timerId) {
    this.timerId = timerId;
  }

  public long getLastRequestId() {
    return lastRequestId;
  }

  public void setLastRequestId(long requestId) {
    this.lastRequestId = requestId;
  }

  public void timeout() {
    isTimedout = true;
    fail(new RequestTimeoutException("Request timed out after " + timeout));
  }

  public boolean isTimedout() {
    return isTimedout;
  }
}
