/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.logstreams.impl.backpressure;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

public final class AppendBackpressureMetrics {

  private static final Counter TOTAL_DEFERRED_APPEND_COUNT =
      Counter.build()
          .namespace("zeebe")
          .name("deferred_append_count_total")
          .help("Number of deferred appends due to backpressure")
          .labelNames("partition")
          .register();

  private static final Counter TOTAL_APPEND_TRY_COUNT =
      Counter.build()
          .namespace("zeebe")
          .name("try_to_append_total")
          .help("Number of tries to append")
          .labelNames("partition")
          .register();

  private static final Gauge CURRENT_INFLIGHT =
      Gauge.build()
          .namespace("zeebe")
          .name("backpressure_inflight_append_count")
          .help("Current number of append inflight")
          .labelNames("partition")
          .register();

  private static final Gauge CURRENT_LIMIT =
      Gauge.build()
          .namespace("zeebe")
          .name("backpressure_append_limit")
          .help("Current limit for number of inflight appends")
          .labelNames("partition")
          .register();

  private final int partitionId;

  public AppendBackpressureMetrics(final int partitionId) {
    this.partitionId = partitionId;
  }

  public void deferred() {
    TOTAL_DEFERRED_APPEND_COUNT.labels(String.valueOf(partitionId)).inc();
  }

  public void newEntryToAppend() {
    TOTAL_APPEND_TRY_COUNT.labels(String.valueOf(partitionId)).inc();
  }

  public void incInflight() {
    CURRENT_INFLIGHT.labels(String.valueOf(partitionId)).inc();
  }

  public void decInflight() {
    CURRENT_INFLIGHT.labels(String.valueOf(partitionId)).dec();
  }

  public void setNewLimit(final int newLimit) {
    CURRENT_LIMIT.labels(String.valueOf(partitionId)).set(newLimit);
  }

  public void setInflight(final int count) {
    CURRENT_INFLIGHT.labels(String.valueOf(partitionId)).set(0);
  }
}
