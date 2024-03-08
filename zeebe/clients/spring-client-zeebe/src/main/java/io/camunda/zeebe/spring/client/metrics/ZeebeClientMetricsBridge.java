package io.camunda.zeebe.spring.client.metrics;

import io.camunda.zeebe.client.api.worker.JobWorkerMetrics;

/**
 * Bridge between spring-zeebe metrics and zeebe-client metrics. One way flow where zeebe-client
 * metrics get propagated to MetricRecorder format in Spring.
 */
public class ZeebeClientMetricsBridge implements JobWorkerMetrics {

  private final MetricsRecorder metricsRecorder;
  private final String jobType;

  public ZeebeClientMetricsBridge(MetricsRecorder metricsRecorder, String jobType) {
    this.metricsRecorder = metricsRecorder;
    this.jobType = jobType;
  }

  @Override
  public void jobActivated(int count) {
    metricsRecorder.increase("zeebe.client.worker.job", "activated", jobType, count);
  }

  @Override
  public void jobHandled(int count) {
    metricsRecorder.increase("zeebe.client.worker.job", "handled", jobType, count);
  }
}
