/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.client.api;

<<<<<<< HEAD
import io.camunda.zeebe.broker.client.impl.BrokerClientMetricsImpl;
=======
import static io.camunda.zeebe.broker.client.api.BrokerClientMetricsDoc.FAILED_REQUESTS;
import static io.camunda.zeebe.broker.client.api.BrokerClientMetricsDoc.REQUEST_LATENCY;
import static io.camunda.zeebe.broker.client.api.BrokerClientMetricsDoc.TOTAL_REQUESTS;

import io.camunda.zeebe.broker.client.api.BrokerClientMetricsDoc.RequestKeyNames;
import io.camunda.zeebe.util.collection.Map3D;
import io.camunda.zeebe.util.collection.Table;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.core.instrument.Counter;
>>>>>>> 915f50ef (fix: use correct metric name)
import io.micrometer.core.instrument.MeterRegistry;

public interface BrokerClientRequestMetrics {
  Noop NOOP = new Noop();

  static BrokerClientRequestMetrics of(final MeterRegistry meterRegistry) {
    return new BrokerClientMetricsImpl(meterRegistry);
  }

  void registerSuccessfulRequest(
      final int partitionId, final String requestType, final long latencyMs);

  void registerFailedRequest(final int partitionId, final String requestType, final Enum<?> error);

<<<<<<< HEAD
  final class Noop implements BrokerClientRequestMetrics {
=======
  private Counter registerFailedRequestCounter(
      final int partitionId, final String requestType, final Enum<?> error) {
    return Counter.builder(FAILED_REQUESTS.getName())
        .description(FAILED_REQUESTS.getDescription())
        .tag(RequestKeyNames.REQUEST_TYPE.asString(), requestType)
        .tag(PartitionKeyNames.PARTITION.asString(), String.valueOf(partitionId))
        .tag(RequestKeyNames.ERROR.asString(), error.name())
        .register(registry);
  }
>>>>>>> 915f50ef (fix: use correct metric name)

    @Override
    public void registerSuccessfulRequest(
        final int partitionId, final String requestType, final long latencyMs) {}

    @Override
    public void registerFailedRequest(
        final int partitionId, final String requestType, final Enum<?> error) {}
  }
}
