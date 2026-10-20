/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.config.LoadTesterProperties;
import io.camunda.zeebe.config.StarterProperties;
import io.camunda.zeebe.config.StarterProperties.WorkloadModel;
import io.camunda.zeebe.metrics.ConnectionMonitor;
import io.camunda.zeebe.metrics.StarterMetricsDoc;
import io.camunda.zeebe.metrics.StarterMetricsDoc.StarterMetricKeyNames;
import io.camunda.zeebe.util.PayloadReader;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;

class StarterTest {

  @Test
  void shouldReportGaugeTagsFromProperties() {
    // given
    final var starterProperties = new StarterProperties();
    starterProperties.setProcessId("foobar");
    starterProperties.setThreads(2);
    final var properties = new LoadTesterProperties();
    properties.setStarter(starterProperties);

    final var registry = new SimpleMeterRegistry();

    // when
    new Starter(
        mock(CamundaClient.class),
        mock(CamundaClient.class),
        properties,
        registry,
        mock(PayloadReader.class),
        mock(ConnectionMonitor.class),
        mock(StarterLivenessIndicator.class),
        mock(WebClient.Builder.class),
        new ObjectMapper(),
        mock(ApplicationContext.class));

    // then
    final var gauge = registry.find(StarterMetricsDoc.CLIENT_INFO.getName()).gauge();

    assertThat(gauge).describedAs("client.info gauge should be registered").isNotNull();
    assertThat(gauge.value()).describedAs("client.info gauge value should be 1").isEqualTo(1.0);
    assertThat(gauge.getId().getTags())
        .describedAs("client.info gauge tags should reflect the starter configuration")
        .containsExactlyInAnyOrder(
            Tag.of(StarterMetricKeyNames.NAME.asString(), "starter"),
            Tag.of(StarterMetricKeyNames.PROCESS_ID.asString(), "foobar"),
            Tag.of(StarterMetricKeyNames.NB_THREADS.asString(), "2"));
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldSeparateLoadGenerationFromDataAvailabilityQueries() {
    // given
    final var loaderClient = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    final var queryClient = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    final var instance = mock(ProcessInstanceEvent.class);
    when(instance.getProcessInstanceKey()).thenReturn(1L);
    when(loaderClient
            .newCreateInstanceCommand()
            .bpmnProcessId(anyString())
            .latestVersion()
            .variables(any(Map.class))
            .send())
        .thenReturn(CompletedFuture.of(instance));

    final var starterProperties = new StarterProperties();
    starterProperties.setDurationLimit(1);
    final var properties = new LoadTesterProperties();
    properties.setStarter(starterProperties);
    properties.getOptimize().setReportEvaluationEnabled(false);
    final var payloadReader = mock(PayloadReader.class);
    when(payloadReader.readPayload(any())).thenReturn("{}");

    final var starter =
        new Starter(
            loaderClient,
            queryClient,
            properties,
            new SimpleMeterRegistry(),
            payloadReader,
            mock(ConnectionMonitor.class),
            mock(StarterLivenessIndicator.class),
            mock(WebClient.Builder.class),
            new ObjectMapper(),
            mock(ApplicationContext.class));

    // when
    starter.run();

    // then
    verify(loaderClient, atLeastOnce()).newCreateInstanceCommand();
    verify(loaderClient, never()).newProcessInstanceSearchRequest();
    verify(queryClient, atLeastOnce()).newProcessInstanceSearchRequest();
    verify(queryClient, never()).newCreateInstanceCommand();
    verify(queryClient, never()).newDeployResourceCommand();
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldStopCreatingInstancesAfterInstanceLimit() {
    // given
    final var loaderClient = mockLoaderClient();
    final var starterProperties = new StarterProperties();
    starterProperties.setInstanceLimit(3);
    final var registry = new SimpleMeterRegistry();

    // when
    newStarter(loaderClient, starterProperties, registry).run();

    // then
    final var submitted =
        registry
            .find(StarterMetricsDoc.PROCESS_INSTANCES_SUBMISSIONS.getName())
            .tag(StarterMetricKeyNames.OUTCOME.asString(), "submitted")
            .counter();
    assertThat(submitted).isNotNull();
    assertThat(submitted.count())
        .describedAs("the run ends after exactly the instance limit of start requests")
        .isEqualTo(3);
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldStartTheWholeRateAtOnceEachRateDurationInBurstWorkloadModel() {
    // given
    final var loaderClient = mockLoaderClient();
    // the first burst is sent right away, the run ends when the second burst is due
    final var starterProperties = new StarterProperties();
    starterProperties.setWorkloadModel(WorkloadModel.BURST);
    starterProperties.setRate(5);
    starterProperties.setRateDuration(Duration.ofSeconds(2));
    starterProperties.setDurationLimit(1);
    final var registry = new SimpleMeterRegistry();

    // when
    newStarter(loaderClient, starterProperties, registry).run();

    // then
    assertThat(submissions(registry, "submitted"))
        .describedAs("only the first burst is sent before the run ends")
        .isEqualTo(5);
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldRetryRejectedStartUntilItIsAcceptedAndCountInstancesNotAttempts() {
    // given
    final var loaderClient = mockLoaderClient(rejected(), CompletedFuture.of(acceptedInstance()));
    final var starterProperties = new StarterProperties();
    starterProperties.setInstanceLimit(2);
    starterProperties.setRetryRejectedStarts(true);
    final var registry = new SimpleMeterRegistry();

    // when
    newStarter(loaderClient, starterProperties, registry).run();

    // then
    assertThat(submissions(registry, "submitted"))
        .describedAs("a retry is not a new instance, so the limit counts the first attempts")
        .isEqualTo(2);
    assertThat(submissions(registry, "retried"))
        .describedAs("the rejected start is retried once, then accepted")
        .isEqualTo(1);
    assertThat(capturedVariables(loaderClient))
        .describedAs("three attempts for two instances, the retry reuses the business key")
        .hasSize(3)
        .extracting(variables -> variables.get("businessKey"))
        .containsOnly(1L, 2L);
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldDropRejectedStartByDefault() {
    // given
    final var loaderClient = mockLoaderClient(rejected());
    final var starterProperties = new StarterProperties();
    starterProperties.setInstanceLimit(1);
    final var registry = new SimpleMeterRegistry();

    // when
    newStarter(loaderClient, starterProperties, registry).run();

    // then
    assertThat(submissions(registry, "retried")).isZero();
    assertThat(capturedVariables(loaderClient)).describedAs("only one attempt").hasSize(1);
  }

  private static ProcessInstanceEvent acceptedInstance() {
    final var instance = mock(ProcessInstanceEvent.class);
    when(instance.getProcessInstanceKey()).thenReturn(1L);
    return instance;
  }

  private static CompletedFuture<ProcessInstanceEvent> rejected() {
    return CompletedFuture.failed(new RuntimeException("rejected"));
  }

  private static double submissions(final SimpleMeterRegistry registry, final String outcome) {
    final var counter =
        registry
            .find(StarterMetricsDoc.PROCESS_INSTANCES_SUBMISSIONS.getName())
            .tag(StarterMetricKeyNames.OUTCOME.asString(), outcome)
            .counter();
    assertThat(counter).isNotNull();
    return counter.count();
  }

  /** Answers the start requests in order, the last answer repeats. */
  @SafeVarargs
  private static CamundaClient mockLoaderClient(
      final CompletedFuture<ProcessInstanceEvent>... answers) {
    final var loaderClient = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    when(loaderClient
            .newCreateInstanceCommand()
            .bpmnProcessId(anyString())
            .latestVersion()
            .variables(any(Map.class))
            .send())
        .thenReturn(answers[0], Arrays.copyOfRange(answers, 1, answers.length));
    return loaderClient;
  }

  private static CamundaClient mockLoaderClient() {
    return mockLoaderClient(CompletedFuture.of(acceptedInstance()));
  }

  private static Starter newStarter(
      final CamundaClient loaderClient,
      final StarterProperties starterProperties,
      final SimpleMeterRegistry registry) {
    final var properties = new LoadTesterProperties();
    properties.setStarter(starterProperties);
    properties.setMonitorDataAvailability(false);
    properties.getOptimize().setReportEvaluationEnabled(false);
    final var payloadReader = mock(PayloadReader.class);
    when(payloadReader.readPayload(any())).thenReturn("{}");
    return new Starter(
        loaderClient,
        mock(CamundaClient.class),
        properties,
        registry,
        payloadReader,
        mock(ConnectionMonitor.class),
        mock(StarterLivenessIndicator.class),
        mock(WebClient.Builder.class),
        new ObjectMapper(),
        mock(ApplicationContext.class));
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> capturedVariables(final CamundaClient loaderClient) {
    final var captor = ArgumentCaptor.forClass(Map.class);
    verify(
            loaderClient.newCreateInstanceCommand().bpmnProcessId("benchmark").latestVersion(),
            atLeastOnce())
        .variables(captor.capture());
    // the stubbing in mockLoaderClient is recorded as a call with a null argument
    return captor.getAllValues().stream()
        .filter(Objects::nonNull)
        .map(variables -> (Map<String, Object>) variables)
        .toList();
  }

  private static final class CompletedFuture<T> extends CompletableFuture<T>
      implements CamundaFuture<T> {

    static <T> CompletedFuture<T> of(final T value) {
      final var future = new CompletedFuture<T>();
      future.complete(value);
      return future;
    }

    static <T> CompletedFuture<T> failed(final Throwable error) {
      final var future = new CompletedFuture<T>();
      future.completeExceptionally(error);
      return future;
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning, final Throwable cause) {
      return super.cancel(mayInterruptIfRunning);
    }

    @Override
    public T join(final long timeout, final TimeUnit unit) {
      return super.join();
    }
  }
}
