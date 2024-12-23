/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.metrics;

import io.camunda.client.api.worker.JobWorkerMetrics;

/**
 * Bridge between spring-sdk metrics and camunda-client metrics. One way flow where camunda-client
 * metrics get propagated to MetricRecorder format in Spring.
 */
public class CamundaClientMetricsBridge implements JobWorkerMetrics {

  private final MetricsRecorder metricsRecorder;
  private final String jobType;

  public CamundaClientMetricsBridge(final MetricsRecorder metricsRecorder, final String jobType) {
    this.metricsRecorder = metricsRecorder;
    this.jobType = jobType;
  }

  @Override
  public void jobActivated(final int count) {
    metricsRecorder.increase("camunda.client.worker.job", "activated", jobType, count);
  }

  @Override
  public void jobHandled(final int count) {
    metricsRecorder.increase("camunda.client.worker.job", "handled", jobType, count);
  }
}
