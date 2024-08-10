/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.api;

import io.camunda.zeebe.transport.stream.impl.messages.ErrorCode;

public interface ClientStreamMetrics {
  /** Invoked whenever the count of known servers in the registry changes */
  default void serverCount(final int count) {}

  /** Invoked whenever the total count of clients in the registry changes */
  default void clientCount(final int count) {}

  /** Invoked whenever the count of streams in the registry changes */
  default void aggregatedStreamCount(final int count) {}

  /**
   * Invoked whenever the count of streams in an aggregated stream changes. The expected
   * implementations of this metric is an histogram, or something which records observations
   * individually.
   */
  default void observeAggregatedClientCount(final int count) {}

  /** Invoked after a payload is successfully pushed to a stream */
  default void pushSucceeded() {}

  /** Invoked if pushing a payload to a stream failed */
  default void pushFailed() {}

  /**
   * Invoked when a push failed for a given client, regardless of whether it ultimately succeeded
   * with another.
   *
   * @param code the error code for the given attempt
   */
  default void pushTryFailed(final ErrorCode code) {}

  static ClientStreamMetrics noop() {
    return new ClientStreamMetrics() {};
  }
}
