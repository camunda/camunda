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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.value.JobWorkerValue;
import io.camunda.client.annotation.value.SourceAware.FromAnnotation;
import io.camunda.client.api.worker.BackoffSupplier;
import io.camunda.client.api.worker.JobExceptionHandler;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.client.api.worker.JobWorkerBuilderStep1;
import io.camunda.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep2;
import io.camunda.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep3;
import io.camunda.client.api.worker.JobWorkerMetrics;
import io.camunda.client.metrics.JobWorkerMetricsFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers how {@link JobWorkerFactory} resolves the {@code withLease} property onto the worker
 * builder. Scoped only to {@code withLease}.
 */
public class JobWorkerFactoryTest {

  private CamundaClient camundaClient;
  private JobWorkerBuilderStep3 step3;
  private JobWorkerValue jobWorkerValue;
  private JobWorkerFactory jobWorkerFactory;
  private JobHandlerFactory jobHandlerFactory;

  @BeforeEach
  void setUp() {
    camundaClient = mock(CamundaClient.class);
    final JobWorkerBuilderStep1 step1 = mock(JobWorkerBuilderStep1.class);
    final JobWorkerBuilderStep2 step2 = mock(JobWorkerBuilderStep2.class);
    step3 = mock(JobWorkerBuilderStep3.class);

    when(camundaClient.newWorker()).thenReturn(step1);
    when(step1.jobType(any())).thenReturn(step2);
    when(step2.handler(any())).thenReturn(step3);
    when(step3.name(any())).thenReturn(step3);
    when(step3.backoffSupplier(any())).thenReturn(step3);
    when(step3.streamNoJobsBackoffSupplier(any())).thenReturn(step3);
    when(step3.jobExceptionHandler(any())).thenReturn(step3);
    when(step3.metrics(any())).thenReturn(step3);
    when(step3.withLease(anyBoolean())).thenReturn(step3);
    when(step3.open()).thenReturn(mock(JobWorker.class));

    jobHandlerFactory = mock(JobHandlerFactory.class);
    when(jobHandlerFactory.getJobHandler(any())).thenReturn(mock(JobHandler.class));

    final JobExceptionHandlerSupplier jobExceptionHandlerSupplier =
        mock(JobExceptionHandlerSupplier.class);
    when(jobExceptionHandlerSupplier.getJobExceptionHandler(any()))
        .thenReturn(mock(JobExceptionHandler.class));

    final JobWorkerMetricsFactory jobWorkerMetricsFactory = mock(JobWorkerMetricsFactory.class);
    when(jobWorkerMetricsFactory.createJobWorkerMetrics(any()))
        .thenReturn(mock(JobWorkerMetrics.class));

    jobWorkerFactory =
        new JobWorkerFactory(
            BackoffSupplier.newBackoffBuilder().build(),
            BackoffSupplier.newBackoffBuilder().build(),
            jobExceptionHandlerSupplier,
            jobWorkerMetricsFactory);

    jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setType(new FromAnnotation<>("test"));
    jobWorkerValue.setName(new FromAnnotation<>("test-worker"));
    // maxRetries is unboxed to a primitive int when building the JobExceptionHandlerSupplier
    // context, so it must be set explicitly - the default Empty<>() would NPE on unboxing.
    jobWorkerValue.setMaxRetries(new FromAnnotation<>(3));
  }

  @Test
  void shouldNotRequestLeaseWhenUnset() {
    // given
    // jobWorkerValue.getWithLease() is left at its default Empty<>()

    // when
    jobWorkerFactory.createJobWorker(camundaClient, jobWorkerValue, jobHandlerFactory);

    // then
    verify(step3, never()).withLease(anyBoolean());
  }

  @Test
  void shouldRequestLeaseWhenExplicitlyTrue() {
    // given
    jobWorkerValue.setWithLease(new FromAnnotation<>(true));

    // when
    jobWorkerFactory.createJobWorker(camundaClient, jobWorkerValue, jobHandlerFactory);

    // then
    verify(step3).withLease(true);
  }

  @Test
  void shouldRequestNoLeaseWhenExplicitlyFalse() {
    // given
    jobWorkerValue.setWithLease(new FromAnnotation<>(false));

    // when
    jobWorkerFactory.createJobWorker(camundaClient, jobWorkerValue, jobHandlerFactory);

    // then
    verify(step3).withLease(false);
  }
}
