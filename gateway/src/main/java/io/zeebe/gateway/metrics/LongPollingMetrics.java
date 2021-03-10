/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.metrics;

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
}
