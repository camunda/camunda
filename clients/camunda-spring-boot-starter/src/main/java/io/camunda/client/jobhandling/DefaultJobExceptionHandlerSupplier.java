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

import io.camunda.client.api.worker.BackoffSupplier;
import io.camunda.client.api.worker.JobExceptionHandler;
import io.camunda.client.metrics.MetricsRecorder;
import java.util.concurrent.ScheduledExecutorService;

public class DefaultJobExceptionHandlerSupplier implements JobExceptionHandlerSupplier {
  private final MetricsRecorder metricsRecorder;
  private final BackoffSupplier backoffSupplier;
  private final ScheduledExecutorService scheduledExecutorService;

  public DefaultJobExceptionHandlerSupplier(
      final MetricsRecorder metricsRecorder,
      final BackoffSupplier backoffSupplier,
      final ScheduledExecutorService scheduledExecutorService) {
    this.metricsRecorder = metricsRecorder;
    this.backoffSupplier = backoffSupplier;
    this.scheduledExecutorService = scheduledExecutorService;
  }

  @Override
  public JobExceptionHandler getJobExceptionHandler(
      final JobExceptionHandlerSupplierContext context) {
    return new BeanJobExceptionHandler(
        context.retryBackoff(),
        context.maxRetries(),
        metricsRecorder,
        backoffSupplier,
        scheduledExecutorService);
  }
}
