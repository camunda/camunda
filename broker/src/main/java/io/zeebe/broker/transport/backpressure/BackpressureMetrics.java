/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.transport.backpressure;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

public final class BackpressureMetrics {

  private static final Counter DROPPED_REQUEST_COUNT =
      Counter.build()
          .namespace("zeebe")
          .name("dropped_request_count_total")
          .help("Number of requests dropped due to backpressure")
          .labelNames("partition")
          .register();

  private static final Counter TOTAL_REQUEST_COUNT =
      Counter.build()
          .namespace("zeebe")
          .name("received_request_count_total")
          .help("Number of requests received")
          .labelNames("partition")
          .register();

  private static final Gauge CURRENT_INFLIGHT =
      Gauge.build()
          .namespace("zeebe")
          .name("backpressure_inflight_requests_count")
          .help("Current number of request inflight")
          .labelNames("partition")
          .register();

  private static final Gauge CURRENT_LIMIT =
      Gauge.build()
          .namespace("zeebe")
          .name("backpressure_requests_limit")
          .help("Current limit for number of inflight requests")
          .labelNames("partition")
          .register();

  public void dropped(final int partitionId) {
    DROPPED_REQUEST_COUNT.labels(String.valueOf(partitionId)).inc();
  }

  public void receivedRequest(final int partitionId) {
    TOTAL_REQUEST_COUNT.labels(String.valueOf(partitionId)).inc();
  }

  public void incInflight(final int partitionId) {
    CURRENT_INFLIGHT.labels(String.valueOf(partitionId)).inc();
  }

  public void decInflight(final int partitionId) {
    CURRENT_INFLIGHT.labels(String.valueOf(partitionId)).dec();
  }

  public void setNewLimit(final int partitionId, final int newLimit) {
    CURRENT_LIMIT.labels(String.valueOf(partitionId)).set(newLimit);
  }

  public void setInflight(final int partitionId, final int count) {
    CURRENT_INFLIGHT.labels(String.valueOf(partitionId)).set(0);
  }
}
