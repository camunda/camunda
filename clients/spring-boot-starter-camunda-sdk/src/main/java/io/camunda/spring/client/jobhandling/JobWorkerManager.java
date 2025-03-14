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
import io.camunda.spring.client.jobhandling.parameter.ParameterResolverStrategy;
import io.camunda.spring.client.jobhandling.result.ResultProcessorStrategy;
import io.camunda.spring.client.metrics.CamundaClientMetricsBridge;
import io.camunda.spring.client.metrics.MetricsRecorder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobWorkerManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(JobWorkerManager.class);

  private final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy;
  private final MetricsRecorder metricsRecorder;
  private final ParameterResolverStrategy parameterResolverStrategy;
  private final ResultProcessorStrategy resultProcessorStrategy;
  private final BackoffSupplier backoffSupplier;

  private List<JobWorker> openedWorkers = new ArrayList<>();
  private final List<JobWorkerValue> workerValues = new ArrayList<>();

  public JobWorkerManager(
      final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy,
      final MetricsRecorder metricsRecorder,
      final ParameterResolverStrategy parameterResolverStrategy,
      final ResultProcessorStrategy resultProcessorStrategy,
      final BackoffSupplier backoffSupplier) {
    this.commandExceptionHandlingStrategy = commandExceptionHandlingStrategy;
    this.metricsRecorder = metricsRecorder;
    this.parameterResolverStrategy = parameterResolverStrategy;
    this.resultProcessorStrategy = resultProcessorStrategy;
    this.backoffSupplier = backoffSupplier;
  }

  public JobWorker openWorker(final CamundaClient client, final JobWorkerValue jobWorkerValue) {
    return openWorker(
        client,
        jobWorkerValue,
        new JobHandlerInvokingSpringBeans(
            jobWorkerValue,
            commandExceptionHandlingStrategy,
            metricsRecorder,
            parameterResolverStrategy,
            resultProcessorStrategy));
  }

  public JobWorker openWorker(
      final CamundaClient client, final JobWorkerValue jobWorkerValue, final JobHandler handler) {

    final JobWorkerBuilderStep1.JobWorkerBuilderStep3 builder =
        client
            .newWorker()
            .jobType(jobWorkerValue.getType())
            .handler(handler)
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

    final JobWorker jobWorker = builder.open();
    openedWorkers.add(jobWorker);
    workerValues.add(jobWorkerValue);
    LOGGER.info(". Starting job worker: {}", jobWorkerValue);
    return jobWorker;
  }

  private boolean isValidDuration(final Duration duration) {
    return duration != null && !duration.isNegative();
  }

  public void closeAllOpenWorkers() {
    openedWorkers.forEach(worker -> worker.close());
    openedWorkers = new ArrayList<>();
  }

  public void closeWorker(final JobWorker worker) {
    worker.close();
    final int i = openedWorkers.indexOf(worker);
    openedWorkers.remove(i);
    workerValues.remove(i);
  }

  public Optional<JobWorkerValue> findJobWorkerConfigByName(final String name) {
    return workerValues.stream().filter(worker -> worker.getName().equals(name)).findFirst();
  }

  public Optional<JobWorkerValue> findJobWorkerConfigByType(final String type) {
    return workerValues.stream().filter(worker -> worker.getType().equals(type)).findFirst();
  }
}
