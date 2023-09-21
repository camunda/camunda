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
package io.camunda.zeebe.client.impl.worker;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.command.StreamJobsCommandStep1.StreamJobsCommandStep3;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep3;
import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mockito;

@SuppressWarnings("resource")
class JobWorkerBuilderImplTest {

  private JobWorkerBuilderImpl jobWorkerBuilder;
  private JobClient jobClient;
  private List<Closeable> closeables;

  @BeforeEach
  void setUp() {
    final ZeebeClientConfiguration zeebeClientConfiguration = mock();
    jobClient = mock(JobClient.class, Answers.RETURNS_DEEP_STUBS);
    final ScheduledExecutorService executorService = mock();
    closeables = new ArrayList<>();
    jobWorkerBuilder =
        new JobWorkerBuilderImpl(zeebeClientConfiguration, jobClient, executorService, closeables);
  }

  @AfterEach
  void afterEach() throws IOException {
    for (final Closeable closeable : closeables) {
      closeable.close();
    }
  }

  @Test
  void shouldThrowErrorIfTimeoutIsNegative() {
    // given
    // when
    assertThatThrownBy(
            () ->
                jobWorkerBuilder
                    .jobType("some-type")
                    .handler(mock())
                    .timeout(Duration.ofSeconds(5).negated())
                    .open())
        // then
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("timeout must be not negative");
  }

  @Test
  void shouldThrowErrorIfTimeoutIsZero() {
    // given
    // when
    assertThatThrownBy(
            () ->
                jobWorkerBuilder.jobType("some-type").handler(mock()).timeout(Duration.ZERO).open())
        // then
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("timeout must be not zero");
  }

  @Test
  void shouldNotUseStreamingIfNotOptedIn() {
    // given
    final JobWorkerBuilderStep3 builder =
        jobWorkerBuilder
            .jobType("type")
            .handler((c, j) -> {})
            .timeout(1)
            .name("test")
            .maxJobsActive(30);

    // when
    builder.open();

    // then
    verify(jobClient, never()).newStreamJobsCommand();
  }

  @Test
  void shouldUseStreamingIfOptedIn() {
    // given - when
    final JobWorkerBuilderStep3 builder =
        jobWorkerBuilder
            .jobType("type")
            .handler((c, j) -> {})
            .timeout(1)
            .name("test")
            .maxJobsActive(30);

    // when
    builder.streamEnabled(true).open();

    // then
    verify(jobClient, atLeast(1)).newStreamJobsCommand();
  }

  @Test
  void shouldUseStreamTimeoutInsteadOfRequestTimeout() {
    // given
    final StreamJobsCommandStep3 lastStep = Mockito.mock(Answers.RETURNS_SELF);
    Mockito.when(jobClient.newStreamJobsCommand().jobType(anyString()).consumer(any()))
        .thenReturn(lastStep);
    Mockito.when(lastStep.tenantIds(anyList())).thenReturn(lastStep);
    Mockito.when(lastStep.send()).thenReturn(Mockito.mock());

    // when
    jobWorkerBuilder
        .jobType("type")
        .handler((c, j) -> {})
        .requestTimeout(Duration.ofSeconds(10))
        .streamTimeout(Duration.ofHours(5))
        .timeout(1)
        .name("test")
        .maxJobsActive(30)
        .streamEnabled(true)
        .open();

    // then
    verify(lastStep, atLeast(1)).requestTimeout(Duration.ofHours(5));
  }

  @Test
  void shouldTimeoutStreamAfterEightHours() {
    // given
    final StreamJobsCommandStep3 lastStep = Mockito.mock(Answers.RETURNS_SELF);
    Mockito.when(jobClient.newStreamJobsCommand().jobType(anyString()).consumer(any()))
        .thenReturn(lastStep);
    Mockito.when(lastStep.tenantIds(anyList())).thenReturn(lastStep);
    Mockito.when(lastStep.send()).thenReturn(Mockito.mock());

    // when
    jobWorkerBuilder
        .jobType("type")
        .handler((c, j) -> {})
        .timeout(1)
        .name("test")
        .maxJobsActive(30)
        .streamEnabled(true)
        .open();

    // then
    verify(lastStep, atLeast(1)).requestTimeout(Duration.ofHours(8));
  }
}
