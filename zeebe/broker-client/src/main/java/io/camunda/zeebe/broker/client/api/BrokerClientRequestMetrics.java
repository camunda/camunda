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

public interface BrokerClientRequestMetrics {
  Noop NOOP = new Noop();

  static BrokerClientRequestMetrics of(final MeterRegistry meterRegistry) {
    return new BrokerClientMetricsImpl(meterRegistry);
  }

  void registerSuccessfulRequest(
      final int partitionId, final String requestType, final long latencyMs);

  void registerFailedRequest(final int partitionId, final String requestType, final Enum<?> error);

  final class Noop implements BrokerClientRequestMetrics {

    @Override
    public void registerSuccessfulRequest(
        final int partitionId, final String requestType, final long latencyMs) {}

    @Override
    public void registerFailedRequest(
        final int partitionId, final String requestType, final Enum<?> error) {}
  }
}
