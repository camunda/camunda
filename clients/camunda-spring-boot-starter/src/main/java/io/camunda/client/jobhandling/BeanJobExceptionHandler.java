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

import io.camunda.client.api.command.FailJobCommandStep1.FailJobCommandStep2;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.command.ThrowErrorCommandStep1.ThrowErrorCommandStep2;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.FailJobResponse;
import io.camunda.client.api.response.ThrowErrorResponse;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.exception.BpmnError;
import io.camunda.client.exception.JobError;
import io.camunda.client.impl.worker.JobExceptionHandlerImpl;
import io.camunda.client.metrics.JobHandlerMetrics;
import io.camunda.client.metrics.MetricsRecorder;
import io.camunda.client.metrics.MetricsRecorder.CounterMetricsContext;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BeanJobExceptionHandler extends JobExceptionHandlerImpl {
  private static final Logger LOG = LoggerFactory.getLogger(BeanJobExceptionHandler.class);
  private final MetricsRecorder metricsRecorder;
  private final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy;
  private final int maxRetries;
  private final Duration retryBackoff;

  public BeanJobExceptionHandler(
      final Duration retryBackoff,
      final int maxRetries,
      final MetricsRecorder metricsRecorder,
      final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy) {
    super(
        DEFAULT_ERROR_MESSAGE_PROVIDER,
        DEFAULT_RETRIES_PROVIDER,
        ctx -> retryBackoff,
        DEFAULT_VARIABLES_PROVIDER);
    this.metricsRecorder = metricsRecorder;
    this.commandExceptionHandlingStrategy = commandExceptionHandlingStrategy;
    this.maxRetries = maxRetries;
    this.retryBackoff = retryBackoff;
  }

  private CommandWrapper createCommandWrapper(
      final FinalCommandStep<?> command,
      final ActivatedJob job,
      final int maxRetries,
      final CounterMetricsContext metricsContext) {
    return new CommandWrapper(
        command,
        job,
        commandExceptionHandlingStrategy,
        metricsRecorder,
        metricsContext,
        maxRetries);
  }

  @Override
  public void handleJobException(final JobExceptionHandlerContext context) {
    final CounterMetricsContext metricsContext =
        JobHandlerMetrics.counter(context.getActivatedJob());
    final Exception exception = context.getException();
    final ActivatedJob job = context.getActivatedJob();
    final JobClient jobClient = context.getJobClient();
    if (exception instanceof final JobError jobError) {
      LOG.trace("Caught job error on {}", job);
      final CommandWrapper command =
          createCommandWrapper(
              createFailJobCommand(jobClient, context.getActivatedJob(), jobError),
              context.getActivatedJob(),
              maxRetries,
              metricsContext);
      command.executeAsyncWithMetrics(MetricsRecorder::increaseFailed);
    } else if (exception instanceof final BpmnError bpmnError) {
      LOG.trace("Caught BPMN error on {}", job);
      final CommandWrapper command =
          createCommandWrapper(
              createThrowErrorCommand(jobClient, context.getActivatedJob(), bpmnError),
              context.getActivatedJob(),
              maxRetries,
              metricsContext);
      command.executeAsyncWithMetrics(MetricsRecorder::increaseBpmnError);
    } else {
      metricsRecorder.increaseFailed(metricsContext);
      super.handleJobException(context);
    }
  }

  private FinalCommandStep<ThrowErrorResponse> createThrowErrorCommand(
      final JobClient jobClient, final ActivatedJob job, final BpmnError bpmnError) {
    final ThrowErrorCommandStep2 command =
        jobClient
            .newThrowErrorCommand(job.getKey())
            .errorCode(bpmnError.getErrorCode())
            .errorMessage(bpmnError.getErrorMessage());
    return JobHandlingUtil.applyVariables(bpmnError.getVariables(), command);
  }

  private FinalCommandStep<FailJobResponse> createFailJobCommand(
      final JobClient jobClient, final ActivatedJob job, final JobError jobError) {
    final int retries =
        jobError.getRetries() == null ? (job.getRetries() - 1) : jobError.getRetries();
    final String errorMessage = JobHandlingUtil.createErrorMessage(jobError);
    final Duration backoff =
        jobError.getRetryBackoff().apply(retries) == null
            ? retryBackoff
            : jobError.getRetryBackoff().apply(retries);
    final FailJobCommandStep2 command =
        jobClient
            .newFailCommand(job.getKey())
            .retries(retries)
            .errorMessage(errorMessage)
            .retryBackoff(backoff);
    return JobHandlingUtil.applyVariables(jobError.getVariables(), command);
  }
}
