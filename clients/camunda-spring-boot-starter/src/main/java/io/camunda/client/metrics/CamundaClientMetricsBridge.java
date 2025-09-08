/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.metrics;

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
