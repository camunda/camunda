/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.impl;

import io.atomix.utils.net.Address;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;

final class RequestContext {

  private final CompletableActorFuture<DirectBuffer> currentFuture;
  private final Supplier<String> nodeAddressSupplier;
  private final String topicName;
  private final byte[] requestBytes;
  private final boolean shouldRetry;
  private final long startTime;
  private final Duration timeout;
  private final Predicate<DirectBuffer> responseValidator;

  private ScheduledTimer scheduledTimer;

  RequestContext(
      final CompletableActorFuture<DirectBuffer> currentFuture,
      final Supplier<String> nodeAddressSupplier,
      final String topicName,
      final byte[] requestBytes,
      final Predicate<DirectBuffer> responseValidator,
      final boolean shouldRetry,
      final Duration timeout) {
    this.currentFuture = currentFuture;
    this.nodeAddressSupplier = nodeAddressSupplier;
    this.topicName = topicName;
    this.requestBytes = requestBytes;
    this.shouldRetry = shouldRetry;
    startTime = ActorClock.currentTimeMillis();
    this.responseValidator = responseValidator;
    this.timeout = timeout;
  }

  public boolean isDone() {
    return currentFuture.isDone();
  }

  Address getNodeAddress() {
    final var address = nodeAddressSupplier.get();
    return address == null ? null : Address.from(address);
  }

  String getTopicName() {
    return topicName;
  }

  byte[] getRequestBytes() {
    return requestBytes;
  }

  public Duration getTimeout() {
    return timeout;
  }

  /**
   * @return the time out, which is calculated via given timeout minus the already elapsed time.
   *     this is necessary to respect the retries
   */
  Duration calculateTimeout() {
    final var currentTime = ActorClock.currentTimeMillis();
    final var elapsedTime = currentTime - startTime;

    return timeout.minus(Duration.ofMillis(elapsedTime));
  }

  boolean verifyResponse(final DirectBuffer response) {
    // the predicate returns true when the response is valid and the request should not be retried
    return responseValidator.test(response);
  }

  public void complete(final DirectBuffer buffer) {
    currentFuture.complete(buffer);
    cancelTimer();
  }

  public void completeExceptionally(final Throwable throwable) {
    currentFuture.completeExceptionally(throwable);
    cancelTimer();
  }

  private void cancelTimer() {
    if (scheduledTimer != null) {
      scheduledTimer.cancel();
    }
  }

  public void setScheduledTimer(final ScheduledTimer scheduledTimer) {
    this.scheduledTimer = scheduledTimer;
  }

  public void timeout() {
    currentFuture.completeExceptionally(
        new TimeoutException("Request timed out after " + timeout.toString()));
  }

  public boolean shouldRetry() {
    return shouldRetry;
  }
}
