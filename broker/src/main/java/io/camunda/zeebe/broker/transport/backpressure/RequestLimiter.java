/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.transport.backpressure;

public interface RequestLimiter<C> {

  /**
   * Try to add to the inflight requests. If success, {@link #onResponse(int, long)} ()} must be
   * called when the request * is processed.
   *
   * @param streamId
   * @param requestId
   * @param context some limiters may use additional context to decide if a request should be
   *     accepted
   * @return true if request is added to the inflight requests, false otherwise
   */
  boolean tryAcquire(int streamId, long requestId, C context);

  /**
   * Notify when a request processing is completed by calling this method.
   *
   * @param streamId
   * @param requestId
   */
  void onResponse(int streamId, long requestId);

  /**
   * Notify when a request is cancelled after {@link #tryAcquire(int, long, Object)} was success.
   *
   * @param streamId
   * @param requestId
   */
  void onIgnore(int streamId, long requestId);

  int getLimit();

  int getInflightCount();
}
