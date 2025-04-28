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
import io.camunda.client.api.worker.JobClient;
import io.camunda.spring.client.exception.BpmnError;
import io.camunda.spring.client.exception.JobError;
import io.camunda.spring.client.metrics.MetricsRecorder;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultJobExceptionHandlingStrategy implements JobExceptionHandlingStrategy {
  private static final Logger LOG =
      LoggerFactory.getLogger(DefaultJobExceptionHandlingStrategy.class);

  @Override
  public void handleException(
      final JobClient jobClient,
      final ActivatedJob job,
      final Exception exception,
      final CommandWrapperCreator commandWrapperCreator)
      throws Exception {
    if (exception instanceof final JobError jobError) {
      LOG.trace("Caught job error on {}", job);
      final CommandWrapper command =
          commandWrapperCreator.create(createFailJobCommand(jobClient, job, jobError));
      command.executeAsync();
    } else if (exception instanceof final BpmnError bpmnError) {
      LOG.trace("Caught BPMN error on {}", job);
      final CommandWrapper command =
          commandWrapperCreator.create(createThrowErrorCommand(jobClient, job, bpmnError));
      command.executeAsyncWithMetrics(
          MetricsRecorder.METRIC_NAME_JOB, MetricsRecorder.ACTION_BPMN_ERROR, job.getType());
    } else {
      throw exception;
    }
  }

  private FinalCommandStep<Void> createThrowErrorCommand(
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
    final StringWriter stringWriter = new StringWriter();
    final PrintWriter printWriter = new PrintWriter(stringWriter);
    jobError.printStackTrace(printWriter);
    final String message = stringWriter.toString();
    final Duration backoff =
        jobError.getRetryBackoff() == null ? Duration.ZERO : jobError.getRetryBackoff();
    final FailJobCommandStep2 command =
        jobClient
            .newFailCommand(job.getKey())
            .retries(retries)
            .errorMessage(message)
            .retryBackoff(backoff);
    return JobHandlingUtil.applyVariables(jobError.getVariables(), command);
  }
}
