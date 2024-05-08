/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

public interface RequestLimiter<C> {

  /**
   * Try to add to the inflight requests. If success, {@link #onResponse(int, long)} ()} must be
   * called when the request * is processed.
   *
   * @param context some limiters may use additional context to decide if a request should be
   *     accepted
   * @return true if request is added to the inflight requests, false otherwise
   */
  boolean tryAcquire(int streamId, long requestId, C context);

  /** Notify when a request processing is completed by calling this method. */
  void onResponse(int streamId, long requestId);

  /**
   * Notify when a request is cancelled after {@link #tryAcquire(int, long, Object)} was success.
   */
  void onIgnore(int streamId, long requestId);

  int getLimit();

  int getInflightCount();
}
