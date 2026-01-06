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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.FailJobCommandStep1;
import io.camunda.client.api.command.FailJobCommandStep1.FailJobCommandStep2;
import io.camunda.client.api.command.ThrowErrorCommandStep1;
import io.camunda.client.api.command.ThrowErrorCommandStep1.ThrowErrorCommandStep2;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.FailJobResponse;
import io.camunda.client.api.response.ThrowErrorResponse;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobExceptionHandler.JobExceptionHandlerContext;
import io.camunda.client.exception.BpmnError;
import io.camunda.client.exception.JobError;
import io.camunda.client.metrics.DefaultNoopMetricsRecorder;
import io.camunda.client.metrics.MetricsRecorder;
import io.camunda.client.spring.test.util.JobWorkerPermutationsGenerator.JobResponse;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

public class BeanJobExceptionHandlerTest {
  @Test
  void shouldHandleAnyException() {
    final MetricsRecorder metricsRecorder = new DefaultNoopMetricsRecorder();
    final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy =
        mock(CommandExceptionHandlingStrategy.class);
    final BeanJobExceptionHandler handler =
        new BeanJobExceptionHandler(
            Duration.ZERO, 0, metricsRecorder, commandExceptionHandlingStrategy);
    final JobClient jobClient = mock(JobClient.class);
    final FailJobCommandStep1 failJobCommandStep1 = mock(FailJobCommandStep1.class);
    final FailJobCommandStep2 failJobCommandStep2 = mock(FailJobCommandStep2.class);
    final CamundaFuture<FailJobResponse> future = mock(CamundaFuture.class);
    when(jobClient.newFailCommand(anyLong())).thenReturn(failJobCommandStep1);
    when(failJobCommandStep1.retries(anyInt())).thenReturn(failJobCommandStep2);
    when(failJobCommandStep2.errorMessage(any())).thenReturn(failJobCommandStep2);
    when(failJobCommandStep2.retryBackoff(any())).thenReturn(failJobCommandStep2);
    when(failJobCommandStep2.variables(any(JobResponse.class))).thenReturn(failJobCommandStep2);
    when(failJobCommandStep2.send()).thenReturn(future);
    when(future.thenApply(any())).thenReturn(mock(CompletionStage.class));
    final ActivatedJob job = mock(ActivatedJob.class);
    when(job.getType()).thenReturn("test");
    when(job.getRetries()).thenReturn(3);
    handler.handleJobException(new JobExceptionHandlerContext(jobClient, job, new Exception()));
    verify(jobClient, times(0)).newCompleteCommand(anyLong());
    verify(jobClient, times(1)).newFailCommand(anyLong());
    verify(jobClient, times(0)).newThrowErrorCommand(anyLong());
    verify(failJobCommandStep2, times(1)).send();
  }

  @Test
  void shouldHandleJobError() {
    final MetricsRecorder metricsRecorder = new DefaultNoopMetricsRecorder();
    final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy =
        mock(CommandExceptionHandlingStrategy.class);
    final BeanJobExceptionHandler handler =
        new BeanJobExceptionHandler(
            Duration.ZERO, 0, metricsRecorder, commandExceptionHandlingStrategy);
    final JobClient jobClient = mock(JobClient.class);
    final FailJobCommandStep1 failJobCommandStep1 = mock(FailJobCommandStep1.class);
    final FailJobCommandStep2 failJobCommandStep2 = mock(FailJobCommandStep2.class);
    final CamundaFuture<FailJobResponse> future = mock(CamundaFuture.class);
    when(jobClient.newFailCommand(anyLong())).thenReturn(failJobCommandStep1);
    when(failJobCommandStep1.retries(anyInt())).thenReturn(failJobCommandStep2);
    when(failJobCommandStep2.errorMessage(any())).thenReturn(failJobCommandStep2);
    when(failJobCommandStep2.retryBackoff(any())).thenReturn(failJobCommandStep2);
    when(failJobCommandStep2.variables(any(JobResponse.class))).thenReturn(failJobCommandStep2);
    when(failJobCommandStep2.send()).thenReturn(future);
    when(future.thenApply(any())).thenReturn(mock(CompletionStage.class));
    final ActivatedJob job = mock(ActivatedJob.class);
    when(job.getType()).thenReturn("test");
    when(job.getRetries()).thenReturn(3);
    handler.handleJobException(
        new JobExceptionHandlerContext(jobClient, job, new JobError("test error")));
    verify(jobClient, times(0)).newCompleteCommand(anyLong());
    verify(jobClient, times(1)).newFailCommand(anyLong());
    verify(jobClient, times(0)).newThrowErrorCommand(anyLong());
    verify(failJobCommandStep2, times(1)).send();
  }

  @Test
  void shouldHandleBpmnError() {
    final MetricsRecorder metricsRecorder = new DefaultNoopMetricsRecorder();
    final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy =
        mock(CommandExceptionHandlingStrategy.class);
    final BeanJobExceptionHandler handler =
        new BeanJobExceptionHandler(
            Duration.ZERO, 0, metricsRecorder, commandExceptionHandlingStrategy);
    final JobClient jobClient = mock(JobClient.class);
    final ThrowErrorCommandStep1 throwErrorCommandStep1 = mock(ThrowErrorCommandStep1.class);
    final ThrowErrorCommandStep2 throwErrorCommandStep2 = mock(ThrowErrorCommandStep2.class);
    final CamundaFuture<ThrowErrorResponse> future = mock(CamundaFuture.class);
    when(jobClient.newThrowErrorCommand(anyLong())).thenReturn(throwErrorCommandStep1);
    when(throwErrorCommandStep1.errorCode(any())).thenReturn(throwErrorCommandStep2);
    when(throwErrorCommandStep2.errorMessage(any())).thenReturn(throwErrorCommandStep2);
    when(throwErrorCommandStep2.variables(any(JobResponse.class)))
        .thenReturn(throwErrorCommandStep2);
    when(throwErrorCommandStep2.send()).thenReturn(future);
    when(future.thenApply(any())).thenReturn(mock(CompletionStage.class));
    final ActivatedJob job = mock(ActivatedJob.class);
    when(job.getType()).thenReturn("test");
    when(job.getRetries()).thenReturn(3);
    handler.handleJobException(
        new JobExceptionHandlerContext(jobClient, job, new BpmnError("errorCode", "test error")));
    verify(jobClient, times(0)).newCompleteCommand(anyLong());
    verify(jobClient, times(0)).newFailCommand(anyLong());
    verify(jobClient, times(1)).newThrowErrorCommand(anyLong());
    verify(throwErrorCommandStep2, times(1)).send();
  }
}
