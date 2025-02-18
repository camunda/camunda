/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.client.api;

import io.camunda.zeebe.broker.client.impl.BrokerClientMetricsImpl;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Metrics related to gateway to broker clients, such as request count, number of failed requests,
 * etc. See {@link BrokerClientMetricsDoc} for documentation on the actual metrics.
 */
public interface BrokerClientRequestMetrics {
  Noop NOOP = new Noop();

  /** Returns an implementation which will register and updates metrics on the given registry */
  static BrokerClientRequestMetrics of(final MeterRegistry meterRegistry) {
    return new BrokerClientMetricsImpl(meterRegistry);
  }

  /** Increments the count of successful requests for the given parameters, using these as tags. */
  void registerSuccessfulRequest(
      final int partitionId, final String requestType, final long latencyMs);

  /** Increments the count of failed requests for the given parameters, using these as tags. */
  void registerFailedRequest(final int partitionId, final String requestType, final Enum<?> error);

  /** An implementation which simply does nothing, mostly useful for testing. */
  final class Noop implements BrokerClientRequestMetrics {

    @Override
    public void registerSuccessfulRequest(
        final int partitionId, final String requestType, final long latencyMs) {}

    @Override
    public void registerFailedRequest(
        final int partitionId, final String requestType, final Enum<?> error) {}
  }
}
