/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl.log;

import io.prometheus.client.Gauge;

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
}
