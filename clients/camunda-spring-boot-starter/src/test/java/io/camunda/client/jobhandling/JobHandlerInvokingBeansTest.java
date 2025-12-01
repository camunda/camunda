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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.value.JobWorkerValue;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.CompleteJobResponse;
import io.camunda.client.api.worker.BackoffSupplier;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.bean.MethodInfo;
import io.camunda.client.exception.BpmnError;
import io.camunda.client.exception.JobError;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.jobhandling.JobHandlerFactory.JobHandlerFactoryContext;
import io.camunda.client.jobhandling.parameter.DefaultParameterResolverStrategy;
import io.camunda.client.jobhandling.parameter.ParameterResolverStrategy;
import io.camunda.client.jobhandling.result.DefaultDocumentResultProcessorFailureHandlingStrategy;
import io.camunda.client.jobhandling.result.DefaultResultProcessorStrategy;
import io.camunda.client.jobhandling.result.ResultProcessorStrategy;
import io.camunda.client.metrics.DefaultNoopMetricsRecorder;
import io.camunda.client.metrics.MetricsRecorder;
import io.camunda.client.spring.test.util.JobWorkerPermutations;
import io.camunda.client.spring.test.util.JobWorkerPermutationsGenerator.AutoComplete;
import io.camunda.client.spring.test.util.JobWorkerPermutationsGenerator.JobResponse;
import io.camunda.client.spring.test.util.JobWorkerPermutationsGenerator.Response;
import io.camunda.client.spring.test.util.JobWorkerPermutationsGenerator.TestDimension;
import io.camunda.client.spring.testsupport.JobWorkerPermutationsUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

public class JobHandlerInvokingBeansTest {

  @ParameterizedTest
  @EnumSource(
      value = Response.class,
      names = {"VOID", "RESPONSE"})
  void shouldAutoComplete(final Response response) throws Exception {
    final TestDimension testDimension = new TestDimension(AutoComplete.YES, response, List.of());
    final JobWorkerValue jobWorkerValue = jobWorkerValue(testDimension);
    final JobHandler jobHandler =
        new BeanJobHandlerFactory(
                methodInfo(testDimension),
                commandExceptionHandlingStrategy(),
                parameterResolverStrategy(),
                resultProcessorStrategy(),
                metricsRecorder())
            .getJobHandler(new JobHandlerFactoryContext(jobWorkerValue, mock(CamundaClient.class)));
    final JobClient jobClient = mock(JobClient.class);
    final CompleteJobCommandStep1 completeJobCommandStep1 = mock(CompleteJobCommandStep1.class);
    final CamundaFuture<CompleteJobResponse> future = mock(CamundaFuture.class);
    when(future.thenApply(any())).thenReturn(mock(CompletionStage.class));
    when(completeJobCommandStep1.variables(any(JobResponse.class)))
        .thenReturn(completeJobCommandStep1);
    when(completeJobCommandStep1.send()).thenReturn(future);
    when(jobClient.newCompleteCommand(anyLong())).thenReturn(completeJobCommandStep1);
    final ActivatedJob job = mock(ActivatedJob.class);
    when(job.getType()).thenReturn("test");
    jobHandler.handle(jobClient, job);
    verify(jobClient, times(1)).newCompleteCommand(anyLong());
    verify(jobClient, times(0)).newFailCommand(anyLong());
    verify(jobClient, times(0)).newThrowErrorCommand(anyLong());
    verify(completeJobCommandStep1, times(1)).send();
  }

  @Test
  void shouldNotAutoComplete() throws Exception {
    final TestDimension testDimension =
        new TestDimension(AutoComplete.NO, Response.VOID, List.of());
    final JobWorkerValue jobWorkerValue = jobWorkerValue(testDimension);
    final JobHandler jobHandler =
        new BeanJobHandlerFactory(
                methodInfo(testDimension),
                commandExceptionHandlingStrategy(),
                parameterResolverStrategy(),
                resultProcessorStrategy(),
                metricsRecorder())
            .getJobHandler(new JobHandlerFactoryContext(jobWorkerValue, mock(CamundaClient.class)));
    final JobClient jobClient = mock(JobClient.class);
    final ActivatedJob job = mock(ActivatedJob.class);
    when(job.getType()).thenReturn("test");
    jobHandler.handle(jobClient, job);
    verify(jobClient, times(0)).newCompleteCommand(anyLong());
    verify(jobClient, times(0)).newFailCommand(anyLong());
    verify(jobClient, times(0)).newThrowErrorCommand(anyLong());
  }

  @ParameterizedTest
  @MethodSource("shouldFailJobSource")
  void shouldFailJob(final AutoComplete autoComplete, final Response response) throws Exception {
    final TestDimension testDimension = new TestDimension(autoComplete, response, List.of());
    final JobWorkerValue jobWorkerValue = jobWorkerValue(testDimension);
    final JobHandler jobHandler =
        new BeanJobHandlerFactory(
                methodInfo(testDimension),
                commandExceptionHandlingStrategy(),
                parameterResolverStrategy(),
                resultProcessorStrategy(),
                metricsRecorder())
            .getJobHandler(new JobHandlerFactoryContext(jobWorkerValue, mock(CamundaClient.class)));

    final JobClient jobClient = mock(JobClient.class);
    final ActivatedJob job = mock(ActivatedJob.class);
    when(job.getType()).thenReturn("test");
    assertThatThrownBy(() -> jobHandler.handle(jobClient, job))
        .isInstanceOf(JobError.class)
        .hasMessage("test error");
  }

  @ParameterizedTest
  @MethodSource("shouldThrowBpmnErrorSource")
  void shouldThrowBpmnError(final AutoComplete autoComplete, final Response response)
      throws Exception {
    final TestDimension testDimension = new TestDimension(autoComplete, response, List.of());
    final JobWorkerValue jobWorkerValue = jobWorkerValue(testDimension);
    final JobHandler jobHandler =
        new BeanJobHandlerFactory(
                methodInfo(testDimension),
                commandExceptionHandlingStrategy(),
                parameterResolverStrategy(),
                resultProcessorStrategy(),
                metricsRecorder())
            .getJobHandler(new JobHandlerFactoryContext(jobWorkerValue, mock(CamundaClient.class)));

    final JobClient jobClient = mock(JobClient.class);
    final ActivatedJob job = mock(ActivatedJob.class);
    when(job.getType()).thenReturn("test");
    assertThatThrownBy(() -> jobHandler.handle(jobClient, job))
        .isInstanceOf(BpmnError.class)
        .hasMessage("[testCode] test message")
        .hasFieldOrPropertyWithValue("errorCode", "testCode");
  }

  private ParameterResolverStrategy parameterResolverStrategy() {
    return new DefaultParameterResolverStrategy(new CamundaObjectMapper());
  }

  private static ResultProcessorStrategy resultProcessorStrategy() {
    return new DefaultResultProcessorStrategy(
        new DefaultDocumentResultProcessorFailureHandlingStrategy());
  }

  private static JobWorkerValue jobWorkerValue(final TestDimension testDimension) {
    return JobWorkerPermutationsUtil.jobWorkerValue(JobWorkerPermutations.class, testDimension);
  }

  private static MethodInfo methodInfo(final TestDimension testDimension) {
    return JobWorkerPermutationsUtil.getMethodInfo(JobWorkerPermutations.class, testDimension);
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
        CamundaClientExecutorService.createDefault().getScheduledExecutor());
  }

  private static MetricsRecorder metricsRecorder() {
    return new DefaultNoopMetricsRecorder();
  }
}
