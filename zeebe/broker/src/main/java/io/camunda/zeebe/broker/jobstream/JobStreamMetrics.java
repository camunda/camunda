/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.jobstream;

import io.camunda.zeebe.transport.stream.api.RemoteStreamMetrics;
import io.camunda.zeebe.transport.stream.impl.messages.ErrorCode;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

public class JobStreamMetrics implements RemoteStreamMetrics {
  private static final String NAMESPACE = "zeebe_broker";

  private static final Gauge STREAM_COUNT =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("open_job_stream_count")
          .help("Number of open job streams in broker")
          .register();

  private static final Counter PUSH_SUCCESS_COUNT =
      Counter.build()
          .namespace(NAMESPACE)
          .name("jobs_pushed_count")
          .help("Total number of jobs pushed to all streams")
          .register();

  private static final Counter PUSH_FAILED_COUNT =
      Counter.build()
          .namespace(NAMESPACE)
          .name("jobs_push_fail_count")
          .help("Total number of failures when pushing jobs to the streams")
          .register();

  private static final Counter PUSH_TRY_FAILED_COUNT =
      Counter.build()
          .namespace(NAMESPACE)
          .name("jobs_push_fail_try_count")
          .help("Total number of failed attempts when pushing jobs to the streams, grouped by code")
          .labelNames("code")
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

  @Override
  public void pushTryFailed(final ErrorCode code) {
    PUSH_TRY_FAILED_COUNT.labels(code.name()).inc();
  }
}
