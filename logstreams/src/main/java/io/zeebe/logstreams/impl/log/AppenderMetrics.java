/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl.log;

import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;

public class AppenderMetrics {

  private static final Gauge LAST_COMMITTED_POSITION =
      Gauge.build()
          .namespace("zeebe")
          .name("log_appender_last_committed_position")
          .help("The last committed position.")
          .labelNames("partition")
          .register();

  private static final Gauge LAST_APPENDED_POSITION =
      Gauge.build()
          .namespace("zeebe")
          .name("log_appender_last_appended_position")
          .help("The last appended position by the appender.")
          .labelNames("partition")
          .register();

  private static final Histogram WRITE_LATENCY =
      Histogram.build()
          .namespace("zeebe")
          .name("log_appender_append_latency")
          .help("Latency to append an event to the log in seconds")
          .labelNames("partition")
          .register();
  private static final Histogram COMMIT_LATENCY =
      Histogram.build()
          .namespace("zeebe")
          .name("log_appender_commit_latency")
          .help("Latency to commit an event to the log in seconds")
          .labelNames("partition")
          .register();

  private final String partitionLabel;

  public AppenderMetrics(final String partitionLabel) {
    this.partitionLabel = partitionLabel;
  }

  public void setLastCommittedPosition(final long position) {
    LAST_COMMITTED_POSITION.labels(partitionLabel).set(position);
  }

  public void setLastAppendedPosition(final long position) {
    LAST_APPENDED_POSITION.labels(partitionLabel).set(position);
  }

  public void appendLatency(final long startTime, final long currentTime) {
    WRITE_LATENCY.labels(partitionLabel).observe((currentTime - startTime) / 1000f);
  }

  public void commitLatency(final long startTime, final long currentTime) {
    COMMIT_LATENCY.labels(partitionLabel).observe((currentTime - startTime) / 1000f);
  }
}
