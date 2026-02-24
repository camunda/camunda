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

import static io.camunda.client.impl.worker.JobWorkerBuilderImpl.DEFAULT_BACKOFF_SUPPLIER;
import static io.camunda.client.impl.worker.JobWorkerBuilderImpl.DEFAULT_STREAM_NO_JOBS_BACKOFF_SUPPLIER;

import io.camunda.client.api.worker.BackoffSupplier;
import io.camunda.client.metrics.JobWorkerMetricsFactory;

public final class JobWorkerFactoryBuilder {
  private BackoffSupplier backoffSupplier = DEFAULT_BACKOFF_SUPPLIER;
  private BackoffSupplier streamNoJobsBackoffSupplier = DEFAULT_STREAM_NO_JOBS_BACKOFF_SUPPLIER;
  private JobExceptionHandlerSupplier jobExceptionHandlerSupplier;
  private JobWorkerMetricsFactory jobWorkerMetricsFactory;

  /**
   * @param backoffSupplier supplies the retry delay after a failed request
   * @return the builder for this worker
   */
  public JobWorkerFactoryBuilder backoffSupplier(final BackoffSupplier backoffSupplier) {
    this.backoffSupplier = backoffSupplier;
    return this;
  }

  /**
   * @param streamNoJobsBackoffSupplier supplies the backoff delay after a successful poll request
   *     when streaming is enabled
   * @return the builder for this worker
   */
  public JobWorkerFactoryBuilder streamNoJobsBackoffSupplier(
      final BackoffSupplier streamNoJobsBackoffSupplier) {
    this.streamNoJobsBackoffSupplier = streamNoJobsBackoffSupplier;
    return this;
  }

  /**
   * @param jobExceptionHandlerSupplier supplies the jobExceptionHandlerSupplier
   * @return the builder for this worker
   */
  public JobWorkerFactoryBuilder jobExceptionHandlerSupplier(
      final JobExceptionHandlerSupplier jobExceptionHandlerSupplier) {
    this.jobExceptionHandlerSupplier = jobExceptionHandlerSupplier;
    return this;
  }

  /**
   * @param jobWorkerMetricsFactory supplies the jobWorkerMetricsFactory
   * @return the builder for this worker
   */
  public JobWorkerFactoryBuilder jobWorkerMetricsFactory(
      final JobWorkerMetricsFactory jobWorkerMetricsFactory) {
    this.jobWorkerMetricsFactory = jobWorkerMetricsFactory;
    return this;
  }

  public JobWorkerFactory build() {
    return new JobWorkerFactory(
        backoffSupplier,
        streamNoJobsBackoffSupplier,
        jobExceptionHandlerSupplier,
        jobWorkerMetricsFactory);
  }
}
