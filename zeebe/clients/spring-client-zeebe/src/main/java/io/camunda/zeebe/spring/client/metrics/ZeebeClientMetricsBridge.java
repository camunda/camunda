/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.spring.client.metrics;

import io.camunda.zeebe.client.api.worker.JobWorkerMetrics;

/**
 * Bridge between spring-zeebe metrics and zeebe-client metrics. One way flow where zeebe-client
 * metrics get propagated to MetricRecorder format in Spring.
 */
public class ZeebeClientMetricsBridge implements JobWorkerMetrics {

  private final MetricsRecorder metricsRecorder;
  private final String jobType;

  public ZeebeClientMetricsBridge(final MetricsRecorder metricsRecorder, final String jobType) {
    this.metricsRecorder = metricsRecorder;
    this.jobType = jobType;
  }

  @Override
  public void jobActivated(final int count) {
    metricsRecorder.increase("zeebe.client.worker.job", "activated", jobType, count);
  }

  @Override
  public void jobHandled(final int count) {
    metricsRecorder.increase("zeebe.client.worker.job", "handled", jobType, count);
  }
}
