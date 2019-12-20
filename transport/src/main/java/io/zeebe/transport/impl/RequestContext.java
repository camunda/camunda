/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl;

import static io.zeebe.transport.impl.AtomixServerTransport.topicName;

import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;

final class RequestContext {

  private final CompletableActorFuture<DirectBuffer> currentFuture;
  private final Supplier<Integer> nodeIdSupplier;
  private final int partitionId;
  private final byte[] requestBytes;
  private final long startTime;
  private final Duration timeout;
  private final Predicate<DirectBuffer> responseValidator;

  RequestContext(
      final CompletableActorFuture<DirectBuffer> currentFuture,
      final Supplier<Integer> nodeIdSupplier,
      final int partitionId,
      final byte[] requestBytes,
      final Predicate<DirectBuffer> responseValidator,
      final Duration timeout) {
    this.currentFuture = currentFuture;
    this.nodeIdSupplier = nodeIdSupplier;
    this.partitionId = partitionId;
    this.requestBytes = requestBytes;
    this.startTime = ActorClock.currentTimeMillis();
    this.responseValidator = responseValidator;
    this.timeout = timeout;
  }

  public boolean isDone() {
    return currentFuture.isDone();
  }

  CompletableActorFuture<DirectBuffer> getCurrentFuture() {
    return currentFuture;
  }

  public Integer getNodeId() {
    return nodeIdSupplier.get();
  }

  String getTopicName() {
    return topicName(partitionId);
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
}
