/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.metrics;

import io.camunda.zeebe.util.VisibleForTesting;
import io.prometheus.client.Gauge;

public final class LongPollingMetrics {
  private static final Gauge REQUESTS_QUEUED_CURRENT =
      Gauge.build()
          .namespace("zeebe")
          .name("long_polling_queued_current")
          .help("Number of requests currently queued due to long polling")
          .labelNames("type")
          .register();

  public void setBlockedRequestsCount(final String type, final int count) {
    REQUESTS_QUEUED_CURRENT.labels(type).set(count);
  }

  @VisibleForTesting("Allows introspecting the long polling state in QA tests")
  public double getBlockedRequestsCount(final String type) {
    return REQUESTS_QUEUED_CURRENT.labels(type).get();
  }
}
