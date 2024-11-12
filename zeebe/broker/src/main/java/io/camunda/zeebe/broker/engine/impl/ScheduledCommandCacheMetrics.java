/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.engine.impl;

import io.camunda.zeebe.protocol.record.intent.Intent;
import io.prometheus.client.Gauge;
import java.util.function.IntConsumer;

/** Defines metrics for scheduled command cache implementations. */
public interface ScheduledCommandCacheMetrics {

  /**
   * Returns a consumer for a given intent, which will be called whenever the underlying cache for
   * this intent changes size.
   */
  IntConsumer forIntent(final Intent intent);

  /**
   * A metrics implementation specifically for the {@link
   * io.camunda.zeebe.broker.engine.impl.BoundedScheduledCommandCache}.
   */
  class BoundedCommandCacheMetrics implements ScheduledCommandCacheMetrics {
    private static final Gauge SIZE =
        Gauge.build()
            .namespace("zeebe")
            .subsystem("stream_processor")
            .name("scheduled_command_cache_size")
            .labelNames("partitionId", "intent")
            .help("Reports the size of each bounded cache per partition and intent")
            .register();

    private final String partitionId;

    public BoundedCommandCacheMetrics(final int partitionId) {
      this.partitionId = String.valueOf(partitionId);
    }

    @Override
    public IntConsumer forIntent(final Intent intent) {
      final var intentLabelValue = intent.getClass().getSimpleName() + "." + intent.name();
      return SIZE.labels(partitionId, intentLabelValue)::set;
    }
  }
}
