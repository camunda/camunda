/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.api;

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

  static ClientStreamMetrics noop() {
    return new ClientStreamMetrics() {};
  }
}
