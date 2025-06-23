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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.FailJobCommandStep1;
import io.camunda.client.api.command.FailJobCommandStep1.FailJobCommandStep2;
import io.camunda.client.api.command.ThrowErrorCommandStep1;
import io.camunda.client.api.command.ThrowErrorCommandStep1.ThrowErrorCommandStep2;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.CompleteJobResponse;
import io.camunda.client.api.response.FailJobResponse;
import io.camunda.client.api.worker.BackoffSupplier;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.spring.client.annotation.value.JobWorkerValue;
import io.camunda.spring.client.jobhandling.parameter.DefaultParameterResolverStrategy;
import io.camunda.spring.client.jobhandling.parameter.ParameterResolver;
import io.camunda.spring.client.jobhandling.result.DefaultResultProcessorStrategy;
import io.camunda.spring.client.jobhandling.result.DocumentResultProcessorFailureHandlingStrategy;
import io.camunda.spring.client.jobhandling.result.ResultProcessor;
import io.camunda.spring.client.metrics.DefaultNoopMetricsRecorder;
import io.camunda.spring.client.metrics.MetricsRecorder;
import io.camunda.spring.client.test.util.JobWorkerPermutations;
import io.camunda.spring.client.test.util.JobWorkerPermutationsGenerator.*;
import io.camunda.spring.client.testsupport.JobWorkerPermutationsUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

public class JobHandlerInvokingSpringBeansTest {

  @ParameterizedTest
  @EnumSource(
      value = Response.class,
      names = {"VOID", "RESPONSE"})
  void shouldAutoComplete(final Response response) throws Exception {
    final JobWorkerValue jobWorkerValue =
        jobWorkerValue(new TestDimension(AutoComplete.YES, response, List.of()));
    final JobHandlerInvokingSpringBeans jobHandler =
        new JobHandlerInvokingSpringBeans(
            jobWorkerValue,
            commandExceptionHandlingStrategy(),
            metricsRecorder(),
            parameterResolvers(jobWorkerValue),
            resultProcessor(jobWorkerValue),
            jobExceptionHandlingStrategy());

    final JobClient jobClient = mock(JobClient.class);
    final CompleteJobCommandStep1 completeJobCommandStep1 = mock(CompleteJobCommandStep1.class);
    final CamundaFuture<CompleteJobResponse> future = mock(CamundaFuture.class);
    when(future.thenApply(any())).thenReturn(mock(CompletionStage.class));
    when(completeJobCommandStep1.variables(any(JobResponse.class)))
        .thenReturn(completeJobCommandStep1);
    when(completeJobCommandStep1.send()).thenReturn(future);
    when(jobClient.newCompleteCommand(anyLong())).thenReturn(completeJobCommandStep1);
    final ActivatedJob job = mock(ActivatedJob.class);
    jobHandler.handle(jobClient, job);
    verify(jobClient, times(1)).newCompleteCommand(anyLong());
    verify(jobClient, times(0)).newFailCommand(anyLong());
    verify(jobClient, times(0)).newThrowErrorCommand(anyLong());
    verify(completeJobCommandStep1, times(1)).send();
  }

  @Test
  void shouldNotAutoComplete() throws Exception {
    final JobWorkerValue jobWorkerValue =
        jobWorkerValue(new TestDimension(AutoComplete.NO, Response.VOID, List.of()));
    final JobHandlerInvokingSpringBeans jobHandler =
        new JobHandlerInvokingSpringBeans(
            jobWorkerValue,
            commandExceptionHandlingStrategy(),
            metricsRecorder(),
            parameterResolvers(jobWorkerValue),
            resultProcessor(jobWorkerValue),
            jobExceptionHandlingStrategy());

    final JobClient jobClient = mock(JobClient.class);
    final ActivatedJob job = mock(ActivatedJob.class);
    jobHandler.handle(jobClient, job);
    verify(jobClient, times(0)).newCompleteCommand(anyLong());
    verify(jobClient, times(0)).newFailCommand(anyLong());
    verify(jobClient, times(0)).newThrowErrorCommand(anyLong());
  }

  @ParameterizedTest
  @MethodSource("shouldFailJobSource")
  void shouldFailJob(final AutoComplete autoComplete, final Response response) throws Exception {
    final JobWorkerValue jobWorkerValue =
        jobWorkerValue(new TestDimension(autoComplete, response, List.of()));
    final JobHandlerInvokingSpringBeans jobHandler =
        new JobHandlerInvokingSpringBeans(
            jobWorkerValue,
            commandExceptionHandlingStrategy(),
            metricsRecorder(),
            parameterResolvers(jobWorkerValue),
            resultProcessor(jobWorkerValue),
            jobExceptionHandlingStrategy());

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
    when(job.getRetries()).thenReturn(3);
    jobHandler.handle(jobClient, job);
    verify(jobClient, times(0)).newCompleteCommand(anyLong());
    verify(jobClient, times(1)).newFailCommand(anyLong());
    verify(jobClient, times(0)).newThrowErrorCommand(anyLong());
    verify(failJobCommandStep2, times(1)).send();
  }

  @ParameterizedTest
  @MethodSource("shouldThrowBpmnErrorSource")
  void shouldThrowBpmnError(final AutoComplete autoComplete, final Response response)
      throws Exception {
    final JobWorkerValue jobWorkerValue =
        jobWorkerValue(new TestDimension(autoComplete, response, List.of()));
    final JobHandlerInvokingSpringBeans jobHandler =
        new JobHandlerInvokingSpringBeans(
            jobWorkerValue,
            commandExceptionHandlingStrategy(),
            metricsRecorder(),
            parameterResolvers(jobWorkerValue),
            resultProcessor(jobWorkerValue),
            jobExceptionHandlingStrategy());

    final JobClient jobClient = mock(JobClient.class);
    final ThrowErrorCommandStep1 throwErrorCommandStep1 = mock(ThrowErrorCommandStep1.class);
    final ThrowErrorCommandStep2 throwErrorCommandStep2 = mock(ThrowErrorCommandStep2.class);
    final CamundaFuture<Void> future = mock(CamundaFuture.class);
    when(jobClient.newThrowErrorCommand(anyLong())).thenReturn(throwErrorCommandStep1);
    when(throwErrorCommandStep1.errorCode(any())).thenReturn(throwErrorCommandStep2);
    when(throwErrorCommandStep2.errorMessage(any())).thenReturn(throwErrorCommandStep2);
    when(throwErrorCommandStep2.variables(any(JobResponse.class)))
        .thenReturn(throwErrorCommandStep2);
    when(throwErrorCommandStep2.send()).thenReturn(future);
    when(future.thenApply(any())).thenReturn(mock(CompletionStage.class));
    final ActivatedJob job = mock(ActivatedJob.class);
    when(job.getRetries()).thenReturn(3);
    jobHandler.handle(jobClient, job);
    verify(jobClient, times(0)).newCompleteCommand(anyLong());
    verify(jobClient, times(0)).newFailCommand(anyLong());
    verify(jobClient, times(1)).newThrowErrorCommand(anyLong());
    verify(throwErrorCommandStep2, times(1)).send();
  }

  private static JobWorkerValue jobWorkerValue(final TestDimension testDimension) {
    return JobWorkerPermutationsUtil.jobWorkerValue(JobWorkerPermutations.class, testDimension);
  }

  private static Stream<Arguments> shouldThrowBpmnErrorSource() {
    final List<Arguments> arguments = new ArrayList<>();
    for (final AutoComplete autoComplete : AutoComplete.values()) {
      for (final Response response : Response.values()) {
        if (response.name().contains("BPMN_ERROR")) {
          arguments.add(Arguments.of(autoComplete, response));
        }
      }
    }
    return arguments.stream();
  }

  private static Stream<Arguments> shouldFailJobSource() {
    final List<Arguments> arguments = new ArrayList<>();
    for (final AutoComplete autoComplete : AutoComplete.values()) {
      for (final Response response : Response.values()) {
        if (response.name().contains("JOB_ERROR")) {
          arguments.add(Arguments.of(autoComplete, response));
        }
      }
    }
    return arguments.stream();
  }

  private static CommandExceptionHandlingStrategy commandExceptionHandlingStrategy() {
    return new DefaultCommandExceptionHandlingStrategy(
        BackoffSupplier.newBackoffBuilder().build(),
        CamundaClientExecutorService.createDefault().get());
  }

  private static MetricsRecorder metricsRecorder() {
    return new DefaultNoopMetricsRecorder();
  }

  private static List<ParameterResolver> parameterResolvers(final JobWorkerValue jobWorkerValue) {
    return JobHandlingUtil.createParameterResolvers(
        new DefaultParameterResolverStrategy(new CamundaObjectMapper()), jobWorkerValue);
  }

  private static ResultProcessor resultProcessor(final JobWorkerValue jobWorkerValue) {
    return JobHandlingUtil.createResultProcessor(
        new DefaultResultProcessorStrategy(
            mock(JobClient.class), mock(DocumentResultProcessorFailureHandlingStrategy.class)),
        jobWorkerValue);
  }

  private static JobExceptionHandlingStrategy jobExceptionHandlingStrategy() {
    return new DefaultJobExceptionHandlingStrategy(
        commandExceptionHandlingStrategy(), metricsRecorder());
  }
}
