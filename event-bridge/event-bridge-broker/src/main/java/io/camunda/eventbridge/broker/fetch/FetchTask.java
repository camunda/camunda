/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.fetch;

import io.camunda.eventbridge.broker.transport.fetch.FetchRequest;
import io.camunda.eventbridge.broker.transport.fetch.FetchResponse;
import java.util.concurrent.CompletableFuture;

/**
 * The execution context for an active fetch operation.
 *
 * <p><b>Design:</b> Bridges the stateless network DTO ({@link FetchRequest}) with the asynchronous
 * response promise. Flattens access to request properties to prevent "wrapper fatigue" in
 * downstream components.
 */
public record FetchTask(
    FetchRequest request, CompletableFuture<FetchResponse> responseFuture, long deadlineMs) {

  public long offset() {
    return request.offset();
  }

  public int minBytes() {
    return request.minBytes();
  }

  public int maxBytes() {
    return request.maxBytes();
  }

  /**
   * Fast-path check to determine if the network connection dropped or the client explicitly
   * canceled the request.
   */
  public boolean isCancelled() {
    return responseFuture.isCancelled();
  }
}
