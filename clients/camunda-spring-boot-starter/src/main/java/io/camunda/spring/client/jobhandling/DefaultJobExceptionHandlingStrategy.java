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

import io.camunda.client.api.command.FailJobCommandStep1.FailJobCommandStep2;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.command.ThrowErrorCommandStep1.ThrowErrorCommandStep2;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.FailJobResponse;
import io.camunda.client.api.response.ThrowErrorResponse;
import io.camunda.client.api.worker.JobClient;
import io.camunda.spring.client.annotation.value.JobWorkerValue;
import io.camunda.spring.client.exception.BpmnError;
import io.camunda.spring.client.exception.JobError;
import io.camunda.spring.client.metrics.MetricsRecorder;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultJobExceptionHandlingStrategy implements JobExceptionHandlingStrategy {
  private static final Logger LOG =
      LoggerFactory.getLogger(DefaultJobExceptionHandlingStrategy.class);
  private final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy;
  private final MetricsRecorder metricsRecorder;

  public DefaultJobExceptionHandlingStrategy(
      final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy,
      final MetricsRecorder metricsRecorder) {
    this.commandExceptionHandlingStrategy = commandExceptionHandlingStrategy;
    this.metricsRecorder = metricsRecorder;
  }

  private CommandWrapper createCommandWrapper(
      final FinalCommandStep<?> command, final ActivatedJob job, final JobWorkerValue workerValue) {
    return new CommandWrapper(
        command,
        job,
        commandExceptionHandlingStrategy,
        metricsRecorder,
        workerValue.getMaxRetries());
  }

  @Override
  public void handleException(final Exception exception, final ExceptionHandlingContext context)
      throws Exception {
    if (exception instanceof final JobError jobError) {
      LOG.trace("Caught job error on {}", context.job());
      final CommandWrapper command =
          createCommandWrapper(
              createFailJobCommand(context.jobClient(), context.job(), jobError),
              context.job(),
              context.jobWorkerValue());
      command.executeAsyncWithMetrics(
          MetricsRecorder.METRIC_NAME_JOB, MetricsRecorder.ACTION_FAILED, context.job().getType());
    } else if (exception instanceof final BpmnError bpmnError) {
      LOG.trace("Caught BPMN error on {}", context.job());
      final CommandWrapper command =
          createCommandWrapper(
              createThrowErrorCommand(context.jobClient(), context.job(), bpmnError),
              context.job(),
              context.jobWorkerValue());
      command.executeAsyncWithMetrics(
          MetricsRecorder.METRIC_NAME_JOB,
          MetricsRecorder.ACTION_BPMN_ERROR,
          context.job().getType());
    } else {
      metricsRecorder.increase(
          MetricsRecorder.METRIC_NAME_JOB, MetricsRecorder.ACTION_FAILED, context.job().getType());
      throw exception;
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
        jobError.getRetryBackoff() == null ? Duration.ZERO : jobError.getRetryBackoff();
    final FailJobCommandStep2 command =
        jobClient
            .newFailCommand(job.getKey())
            .retries(retries)
            .errorMessage(errorMessage)
            .retryBackoff(backoff);
    return JobHandlingUtil.applyVariables(jobError.getVariables(), command);
  }
}
