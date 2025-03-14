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
package io.camunda.zeebe.spring.client.jobhandling;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.worker.BackoffSupplier;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.client.api.worker.JobWorkerBuilderStep1;
import io.camunda.zeebe.spring.client.annotation.value.ZeebeWorkerValue;
import io.camunda.zeebe.spring.client.jobhandling.parameter.ParameterResolverStrategy;
import io.camunda.zeebe.spring.client.jobhandling.result.ResultProcessorStrategy;
import io.camunda.zeebe.spring.client.metrics.MetricsRecorder;
import io.camunda.zeebe.spring.client.metrics.ZeebeClientMetricsBridge;
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
  private final List<ZeebeWorkerValue> workerValues = new ArrayList<>();

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

  public JobWorker openWorker(final ZeebeClient client, final ZeebeWorkerValue zeebeWorkerValue) {
    return openWorker(
        client,
        zeebeWorkerValue,
        new JobHandlerInvokingSpringBeans(
            zeebeWorkerValue,
            commandExceptionHandlingStrategy,
            metricsRecorder,
            parameterResolverStrategy,
            resultProcessorStrategy));
  }

  public JobWorker openWorker(
      final ZeebeClient client, final ZeebeWorkerValue zeebeWorkerValue, final JobHandler handler) {

    final JobWorkerBuilderStep1.JobWorkerBuilderStep3 builder =
        client
            .newWorker()
            .jobType(zeebeWorkerValue.getType())
            .handler(handler)
            .name(zeebeWorkerValue.getName())
            .backoffSupplier(backoffSupplier)
            .metrics(new ZeebeClientMetricsBridge(metricsRecorder, zeebeWorkerValue.getType()));

    if (zeebeWorkerValue.getMaxJobsActive() != null && zeebeWorkerValue.getMaxJobsActive() > 0) {
      builder.maxJobsActive(zeebeWorkerValue.getMaxJobsActive());
    }
    if (isValidDuration(zeebeWorkerValue.getTimeout())) {
      builder.timeout(zeebeWorkerValue.getTimeout());
    }
    if (isValidDuration(zeebeWorkerValue.getPollInterval())) {
      builder.pollInterval(zeebeWorkerValue.getPollInterval());
    }
    if (isValidDuration(zeebeWorkerValue.getRequestTimeout())) {
      builder.requestTimeout(zeebeWorkerValue.getRequestTimeout());
    }
    if (zeebeWorkerValue.getFetchVariables() != null
        && !zeebeWorkerValue.getFetchVariables().isEmpty()) {
      builder.fetchVariables(zeebeWorkerValue.getFetchVariables());
    }
    if (zeebeWorkerValue.getTenantIds() != null && !zeebeWorkerValue.getTenantIds().isEmpty()) {
      builder.tenantIds(zeebeWorkerValue.getTenantIds());
    }
    if (zeebeWorkerValue.getStreamEnabled() != null) {
      builder.streamEnabled(zeebeWorkerValue.getStreamEnabled());
    }
    if (isValidDuration(zeebeWorkerValue.getStreamTimeout())) {
      builder.streamTimeout(zeebeWorkerValue.getStreamTimeout());
    }

    final JobWorker jobWorker = builder.open();
    openedWorkers.add(jobWorker);
    workerValues.add(zeebeWorkerValue);
    LOGGER.info(". Starting Zeebe worker: {}", zeebeWorkerValue);
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

  public Optional<ZeebeWorkerValue> findJobWorkerConfigByName(final String name) {
    return workerValues.stream().filter(worker -> worker.getName().equals(name)).findFirst();
  }

  public Optional<ZeebeWorkerValue> findJobWorkerConfigByType(final String type) {
    return workerValues.stream().filter(worker -> worker.getType().equals(type)).findFirst();
  }
}
