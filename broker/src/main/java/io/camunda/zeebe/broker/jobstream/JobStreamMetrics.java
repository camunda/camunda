/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.jobstream;

import io.camunda.zeebe.transport.stream.impl.RemoteStreamMetrics;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

public class JobStreamMetrics implements RemoteStreamMetrics {
  private static final String NAMESPACE = "zeebe";

  private static final Gauge STREAM_COUNT =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("broker_open_job_stream_count")
          .help("Number of open job streams in broker")
          .register();

  private static final Counter PUSH_SUCCESS_COUNT =
      Counter.build()
          .namespace(NAMESPACE)
          .name("broker_jobs_pushed_count")
          .help("Total number of jobs pushed to all streams")
          .register();

  private static final Counter PUSH_FAILED_COUNT =
      Counter.build()
          .namespace(NAMESPACE)
          .name("broker_jobs_push_fail_count")
          .help("Total number of failures when pushing jobs to the streams")
          .register();

  @Override
  public void addStream() {
    STREAM_COUNT.inc();
  }

  @Override
  public void removeStream() {
    STREAM_COUNT.dec();
  }

  @Override
  public void pushSucceeded() {
    PUSH_SUCCESS_COUNT.inc();
  }

  @Override
  public void pushFailed() {
    PUSH_FAILED_COUNT.inc();
  }
}
