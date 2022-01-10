/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.metrics;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;

public final class ReplayMetrics {

  private static final String LABEL_NAME_PARTITION = "partition";

  private static final String NAMESPACE = "zeebe";

  private static final Counter REPLAY_EVENTS_COUNT =
      Counter.build()
          .namespace(NAMESPACE)
          .name("replay_events_total")
          .help("Number of events replayed by the stream processor.")
          .labelNames(LABEL_NAME_PARTITION)
          .register();

  private static final Gauge LAST_SOURCE_POSITION =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("replay_last_source_position")
          .help("The last source position the stream processor has replayed.")
          .labelNames(LABEL_NAME_PARTITION)
          .register();

  private static final Histogram REPLAY_DURATION =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("replay_event_batch_replay_duration")
          .help("Time for replay a batch of events (in seconds)")
          .labelNames(LABEL_NAME_PARTITION)
          .register();

  private final String partitionIdLabel;

  public ReplayMetrics(final int partitionId) {
    partitionIdLabel = String.valueOf(partitionId);
  }

  public void event() {
    REPLAY_EVENTS_COUNT.labels(partitionIdLabel).inc();
  }

  public Histogram.Timer startReplayDurationTimer() {
    return REPLAY_DURATION.labels(partitionIdLabel).startTimer();
  }

  public void setLastSourcePosition(final long position) {
    LAST_SOURCE_POSITION.labels(partitionIdLabel).set(position);
  }
}
