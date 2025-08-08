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
package io.camunda.spring.client.jobhandling;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.worker.BackoffSupplier;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.client.api.worker.JobWorkerBuilderStep1;
import io.camunda.spring.client.annotation.value.JobWorkerValue;
import io.camunda.spring.client.metrics.CamundaClientMetricsBridge;
import io.camunda.spring.client.metrics.MetricsRecorder;
import java.time.Duration;

public class JobWorkerFactory {
  private final BackoffSupplier backoffSupplier;
  private final MetricsRecorder metricsRecorder;

  public JobWorkerFactory(
      final BackoffSupplier backoffSupplier, final MetricsRecorder metricsRecorder) {
    this.backoffSupplier = backoffSupplier;
    this.metricsRecorder = metricsRecorder;
  }

  public JobWorker createJobWorker(
      final CamundaClient camundaClient,
      final JobWorkerValue jobWorkerValue,
      final JobHandler jobHandler) {
    final JobWorkerBuilderStep1.JobWorkerBuilderStep3 builder =
        camundaClient
            .newWorker()
            .jobType(jobWorkerValue.getType())
            .handler(jobHandler)
            .name(jobWorkerValue.getName())
            .backoffSupplier(backoffSupplier)
            .metrics(new CamundaClientMetricsBridge(metricsRecorder, jobWorkerValue.getType()));

    if (jobWorkerValue.getMaxJobsActive() != null && jobWorkerValue.getMaxJobsActive() > 0) {
      builder.maxJobsActive(jobWorkerValue.getMaxJobsActive());
    }
    if (isValidDuration(jobWorkerValue.getTimeout())) {
      builder.timeout(jobWorkerValue.getTimeout());
    }
    if (isValidDuration(jobWorkerValue.getPollInterval())) {
      builder.pollInterval(jobWorkerValue.getPollInterval());
    }
    if (isValidDuration(jobWorkerValue.getRequestTimeout())) {
      builder.requestTimeout(jobWorkerValue.getRequestTimeout());
    }
    if (jobWorkerValue.getFetchVariables() != null
        && !jobWorkerValue.getFetchVariables().isEmpty()) {
      builder.fetchVariables(jobWorkerValue.getFetchVariables());
    }
    if (jobWorkerValue.getTenantIds() != null && !jobWorkerValue.getTenantIds().isEmpty()) {
      builder.tenantIds(jobWorkerValue.getTenantIds());
    }
    if (jobWorkerValue.getStreamEnabled() != null) {
      builder.streamEnabled(jobWorkerValue.getStreamEnabled());
    }
    if (isValidDuration(jobWorkerValue.getStreamTimeout())) {
      builder.streamTimeout(jobWorkerValue.getStreamTimeout());
    }
    return builder.open();
  }

  private boolean isValidDuration(final Duration duration) {
    return duration != null && !duration.isNegative();
  }
}
