/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.metrics;

import io.prometheus.client.Counter;

public final class BlacklistMetrics {

  private static final Counter BLACKLISTED_INSTANCES_COUNTER =
      Counter.build()
          .namespace("zeebe")
          .name("blacklisted_instances_total")
          .help("Number of blacklisted instances")
          .labelNames("partition")
          .register();

  private final String partitionIdLabel;

  public BlacklistMetrics(final int partitionId) {
    partitionIdLabel = String.valueOf(partitionId);
  }

  public void countBlacklistedInstance() {
    BLACKLISTED_INSTANCES_COUNTER.labels(partitionIdLabel).inc();
  }
}
