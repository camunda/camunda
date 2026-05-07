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
package io.camunda.client.impl.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.ActivateJobsCommandStep1.ActivateJobsCommandStep3;
import io.camunda.client.api.command.StreamJobsCommandStep1.StreamJobsCommandStep3;
import io.camunda.client.api.command.enums.TenantFilter;
import io.camunda.client.api.response.ActivateJobsResponse;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep3;
import io.camunda.client.impl.CamundaClientBuilderImpl;
import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.test.appender.ListAppender;
import org.awaitility.Awaitility;
import org.awaitility.core.ThrowingRunnable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@SuppressWarnings("resource")
class JobWorkerBuilderImplTest {

  private static final String JOB_WORKER_LOGGER_NAME = "io.camunda.client.job.worker";

  private JobWorkerBuilderImpl jobWorkerBuilder;
  private JobClient jobClient;
  private List<Closeable> closeables;
  private CamundaClientConfiguration zeebeClientConfig;
  private ListAppender logCapture;

  @BeforeEach
  void setUp() {
    zeebeClientConfig = new CamundaClientBuilderImpl();
    jobClient = mock(JobClient.class, Answers.RETURNS_DEEP_STUBS);
    final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    closeables = new ArrayList<>();
    jobWorkerBuilder =
        new JobWorkerBuilderImpl(
            zeebeClientConfig, jobClient, executorService, executorService, closeables);

    logCapture = new ListAppender("capture-" + JOB_WORKER_LOGGER_NAME);
    logCapture.start();
    final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    ctx.getConfiguration()
        .getLoggerConfig(JOB_WORKER_LOGGER_NAME)
        .addAppender(logCapture, null, null);
    ctx.updateLoggers();
  }

  @AfterEach
  void afterEach() throws IOException {
    for (final Closeable closeable : closeables) {
      closeable.close();
    }
    if (logCapture != null) {
      final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
      ctx.getConfiguration()
          .getLoggerConfig(JOB_WORKER_LOGGER_NAME)
          .removeAppender(logCapture.getName());
      ctx.updateLoggers();
      logCapture.stop();
    }
  }

  private List<LogEvent> eventsAt(final Level level) {
    return logCapture.getEvents().stream()
        .filter(e -> JOB_WORKER_LOGGER_NAME.equals(e.getLoggerName()))
        .filter(e -> level.equals(e.getLevel()))
        .collect(Collectors.toList());
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
  void shouldThrowErrorIfStreamInactivityTimeoutIsZero() {
    // given / when
    assertThatThrownBy(
            () ->
                jobWorkerBuilder
                    .jobType("some-type")
                    .handler(mock())
                    .streamEnabled(true)
                    .streamInactivityTimeout(Duration.ZERO)
                    .open())
        // then
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("streamInactivityTimeout");
  }

  @Test
  void shouldLogInfoWhenStreamInactivityTimeoutNotLessThanStreamTimeout() {
    // given / when - users may legitimately configure a streamTimeout below the default
    // streamInactivityTimeout to enforce a more frequent hard cutoff. The build must succeed
    // and the runtime must inform the operator that inactivity-based recreation will not fire.
    jobWorkerBuilder
        .jobType("some-type")
        .handler(mock())
        .streamEnabled(true)
        .streamTimeout(Duration.ofMinutes(5))
        .streamInactivityTimeout(Duration.ofMinutes(5))
        .open();

    // then
    final List<LogEvent> infos = eventsAt(Level.INFO);
    assertThat(infos)
        .anySatisfy(
            event ->
                assertThat(event.getMessage().getFormattedMessage())
                    .contains("streamInactivityTimeout")
                    .contains("streamTimeout")
                    .contains("preempt"));
  }

  @Test
  void shouldNotSetRequestTimeoutOnStreamCommand() {
    // given
    final StreamJobsCommandStep3 lastStep = Mockito.mock(Answers.RETURNS_SELF);
    Mockito.when(jobClient.newStreamJobsCommand().jobType(anyString()).consumer(any()))
        .thenReturn(lastStep);
    Mockito.when(lastStep.tenantIds(anyList())).thenReturn(lastStep);
    Mockito.when(lastStep.tenantFilter(any(TenantFilter.class))).thenReturn(lastStep);
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
    verify(lastStep, never()).requestTimeout(any());
  }

  @Test
  void shouldForwardDefaultTenantIdOnPoll() {
    // given
    final ActivateJobsCommandStep3 lastStep = Mockito.mock(Answers.RETURNS_SELF);
    Mockito.when(
            jobClient.newActivateJobsCommand().jobType(anyString()).maxJobsToActivate(anyInt()))
        .thenReturn(lastStep);
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<List<String>> tenantIdCaptor = ArgumentCaptor.forClass(List.class);
    Mockito.when(lastStep.tenantIds(tenantIdCaptor.capture())).thenReturn(lastStep);
    Mockito.when(lastStep.requestTimeout(any())).thenReturn(lastStep);
    final CamundaFuture<ActivateJobsResponse> camundaFuture = Mockito.mock();
    Mockito.when(lastStep.send()).thenReturn(camundaFuture);
    Mockito.when(camundaFuture.exceptionally(any())).thenReturn(Mockito.mock());

    // when
    jobWorkerBuilder
        .jobType("some-type")
        .handler(mock())
        .timeout(Duration.ofSeconds(5))
        .name("worker")
        .maxJobsActive(30)
        .open();

    // then
    await(
        () ->
            assertThat(tenantIdCaptor.getValue())
                .containsOnly(zeebeClientConfig.getDefaultTenantId()));
  }

  @Test
  void shouldForwardCustomTenantIdsOnPoll() {
    // given
    final ActivateJobsCommandStep3 lastStep = Mockito.mock(Answers.RETURNS_SELF);
    Mockito.when(
            jobClient.newActivateJobsCommand().jobType(anyString()).maxJobsToActivate(anyInt()))
        .thenReturn(lastStep);
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<List<String>> tenantIdCaptor = ArgumentCaptor.forClass(List.class);
    Mockito.when(lastStep.tenantIds(tenantIdCaptor.capture())).thenReturn(lastStep);
    Mockito.when(lastStep.requestTimeout(any())).thenReturn(lastStep);
    final CamundaFuture<ActivateJobsResponse> camundaFuture = Mockito.mock();
    Mockito.when(lastStep.send()).thenReturn(camundaFuture);
    Mockito.when(camundaFuture.exceptionally(any())).thenReturn(Mockito.mock());

    // when
    jobWorkerBuilder
        .jobType("some-type")
        .handler(mock())
        .timeout(Duration.ofSeconds(5))
        .name("worker")
        .maxJobsActive(30)
        .tenantIds("1", "2")
        .tenantId("3")
        .tenantIds(Collections.singletonList("4"))
        .open();

    // then
    await(
        () -> assertThat(tenantIdCaptor.getValue()).containsExactlyInAnyOrder("1", "2", "3", "4"));
  }

  @Test
  void shouldForwardDefaultTenantIdOnStream() {
    // given
    final StreamJobsCommandStep3 lastStep = Mockito.mock(Answers.RETURNS_SELF);
    Mockito.when(jobClient.newStreamJobsCommand().jobType(anyString()).consumer(any()))
        .thenReturn(lastStep);
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<List<String>> tenantIdCaptor = ArgumentCaptor.forClass(List.class);
    Mockito.when(lastStep.tenantIds(tenantIdCaptor.capture())).thenReturn(lastStep);
    Mockito.when(lastStep.tenantFilter(any(TenantFilter.class))).thenReturn(lastStep);
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
    await(
        () ->
            assertThat(tenantIdCaptor.getValue())
                .containsOnly(zeebeClientConfig.getDefaultTenantId()));
  }

  @Test
  void shouldForwardCustomTenantIdsOnStream() {
    // given
    final StreamJobsCommandStep3 lastStep = Mockito.mock(Answers.RETURNS_SELF);
    Mockito.when(jobClient.newStreamJobsCommand().jobType(anyString()).consumer(any()))
        .thenReturn(lastStep);
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<List<String>> tenantIdCaptor = ArgumentCaptor.forClass(List.class);
    Mockito.when(lastStep.tenantIds(tenantIdCaptor.capture())).thenReturn(lastStep);
    Mockito.when(lastStep.tenantFilter(any(TenantFilter.class))).thenReturn(lastStep);
    Mockito.when(lastStep.send()).thenReturn(Mockito.mock());

    // when
    jobWorkerBuilder
        .jobType("type")
        .handler((c, j) -> {})
        .timeout(1)
        .name("test")
        .maxJobsActive(30)
        .streamEnabled(true)
        .tenantIds("1", "2")
        .tenantId("3")
        .tenantIds(Collections.singletonList("4"))
        .open();

    // then
    await(
        () -> assertThat(tenantIdCaptor.getValue()).containsExactlyInAnyOrder("1", "2", "3", "4"));
  }

  private void await(final ThrowingRunnable throwingRunnable) {
    Awaitility.await()
        .ignoreExceptions()
        .atMost(Duration.ofSeconds(1))
        .untilAsserted(throwingRunnable);
  }
}
