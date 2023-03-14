/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.jobstream;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

public class StreamMetrics {
  private static final String NAMESPACE = "zeebe";

  private static final Gauge STREAM_COUNT =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("broker_open_stream_count")
          .help("Number of open job streams in broker")
          .register();

  private static final Counter PUSH_SUCCESS_COUNT =
      Counter.build()
          .namespace(NAMESPACE)
          .name("broker_stream_pushed_count")
          .help("Total number of jobs pushed to all streams")
          .register();

  private static final Counter PUSH_FAILED_COUNT =
      Counter.build()
          .namespace(NAMESPACE)
          .name("broker_stream_push_fail_count")
          .help("Total number of failures when pushing jobs to the streams")
          .register();

  void addStream() {
    STREAM_COUNT.inc();
  }

  void removeStream() {
    STREAM_COUNT.dec();
  }

  void pushSucceeded() {
    PUSH_SUCCESS_COUNT.inc();
  }

  void pushFailed() {
    PUSH_FAILED_COUNT.inc();
  }
}
