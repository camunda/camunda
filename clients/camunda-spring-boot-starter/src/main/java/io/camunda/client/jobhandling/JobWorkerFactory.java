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
package io.camunda.client.jobhandling;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.value.JobWorkerValue;
import io.camunda.client.annotation.value.JobWorkerValue.SourceAware;
import io.camunda.client.annotation.value.JobWorkerValue.SourceAware.*;
import io.camunda.client.api.worker.BackoffSupplier;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.client.api.worker.JobWorkerBuilderStep1;
import io.camunda.client.jobhandling.JobExceptionHandlerSupplier.JobExceptionHandlerSupplierContext;
import io.camunda.client.jobhandling.JobHandlerFactory.JobHandlerFactoryContext;
import io.camunda.client.metrics.JobWorkerMetricsFactory;
import io.camunda.client.metrics.JobWorkerMetricsFactory.JobWorkerMetricsFactoryContext;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;

public class JobWorkerFactory {
  private final BackoffSupplier backoffSupplier;
  private final JobExceptionHandlerSupplier jobExceptionHandlerSupplier;
  private final JobWorkerMetricsFactory jobWorkerMetricsFactory;

  public JobWorkerFactory(
      final BackoffSupplier backoffSupplier,
      final JobExceptionHandlerSupplier jobExceptionHandlerSupplier,
      final JobWorkerMetricsFactory jobWorkerMetricsFactory) {
    this.backoffSupplier = backoffSupplier;
    this.jobExceptionHandlerSupplier = jobExceptionHandlerSupplier;
    this.jobWorkerMetricsFactory = jobWorkerMetricsFactory;
  }

  public JobWorker createJobWorker(
      final CamundaClient camundaClient,
      final JobWorkerValue jobWorkerValue,
      final JobHandlerFactory jobHandlerFactory) {
    final JobHandler jobHandler =
        jobHandlerFactory.getJobHandler(
            new JobHandlerFactoryContext(jobWorkerValue, camundaClient));
    final JobWorkerBuilderStep1.JobWorkerBuilderStep3 builder =
        camundaClient
            .newWorker()
            .jobType(jobWorkerValue.getType().value())
            .handler(jobHandler)
            .name(jobWorkerValue.getName().value())
            .backoffSupplier(backoffSupplier)
            .jobExceptionHandler(
                jobExceptionHandlerSupplier.getJobExceptionHandler(
                    new JobExceptionHandlerSupplierContext(
                        jobWorkerValue.getRetryBackoff().value(),
                        jobWorkerValue.getMaxRetries().value())))
            .metrics(
                jobWorkerMetricsFactory.createJobWorkerMetrics(
                    new JobWorkerMetricsFactoryContext(jobWorkerValue.getType().value())));

    if (canBeSetToBuilder(jobWorkerValue.getMaxJobsActive(), this::isValidInteger)) {
      builder.maxJobsActive(jobWorkerValue.getMaxJobsActive().value());
    }
    if (canBeSetToBuilder(jobWorkerValue.getTimeout(), this::isValidDuration)) {
      builder.timeout(jobWorkerValue.getTimeout().value());
    }
    if (canBeSetToBuilder(jobWorkerValue.getPollInterval(), this::isValidDuration)) {
      builder.pollInterval(jobWorkerValue.getPollInterval().value());
    }
    if (canBeSetToBuilder(jobWorkerValue.getRequestTimeout(), this::isValidDuration)) {
      builder.requestTimeout(jobWorkerValue.getRequestTimeout().value());
    }
    if (jobWorkerValue.getFetchVariables() != null
        && !jobWorkerValue.getFetchVariables().isEmpty()) {
      builder.fetchVariables(
          jobWorkerValue.getFetchVariables().stream()
              .map(SourceAware::value)
              .filter(Objects::nonNull)
              .toList());
    }
    if (jobWorkerValue.getTenantFilter() != null) {
      builder.TenantFilter(jobWorkerValue.getTenantFilter().value());
    }
    if (jobWorkerValue.getTenantIds() != null && !jobWorkerValue.getTenantIds().isEmpty()) {
      builder.tenantIds(
          jobWorkerValue.getTenantIds().stream()
              .map(SourceAware::value)
              .filter(Objects::nonNull)
              .toList());
    }
    if (canBeSetToBuilder(jobWorkerValue.getStreamEnabled())) {
      builder.streamEnabled(jobWorkerValue.getStreamEnabled().value());
    }
    if (canBeSetToBuilder(jobWorkerValue.getStreamTimeout(), this::isValidDuration)) {
      builder.streamTimeout(jobWorkerValue.getStreamTimeout().value());
    }
    return builder.open();
  }

  private <T> boolean canBeSetToBuilder(final SourceAware<T> value) {
    return canBeSetToBuilder(value, v -> true);
  }

  private <T> boolean canBeSetToBuilder(final SourceAware<T> value, final Predicate<T> validator) {
    return value.value() != null && !(value instanceof Empty) && validator.test(value.value());
  }

  private boolean isValidDuration(final Duration duration) {
    return duration != null && !duration.isNegative();
  }

  private boolean isValidInteger(final Integer number) {
    return number != null && number >= 0;
  }
}
