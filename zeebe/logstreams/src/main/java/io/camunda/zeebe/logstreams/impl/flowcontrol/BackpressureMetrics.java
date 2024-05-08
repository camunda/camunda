/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import io.prometheus.client.Counter;

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

  public void dropped(final int partitionId) {
    DROPPED_REQUEST_COUNT.labels(String.valueOf(partitionId)).inc();
  }

  public void receivedRequest(final int partitionId) {
    TOTAL_REQUEST_COUNT.labels(String.valueOf(partitionId)).inc();
  }
}
