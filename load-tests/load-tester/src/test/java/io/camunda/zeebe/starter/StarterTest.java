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
import io.camunda.zeebe.metrics.ConnectionMonitor;
import io.camunda.zeebe.metrics.StarterMetricsDoc;
import io.camunda.zeebe.metrics.StarterMetricsDoc.StarterMetricKeyNames;
import io.camunda.zeebe.util.PayloadReader;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
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
  void shouldPassTheSameTimerStartAtToEveryInstance() {
    // given
    final var loaderClient = mockLoaderClient();
    final var starterProperties = new StarterProperties();
    starterProperties.setInstanceLimit(3);
    starterProperties.setTimerStartDelay(Duration.ofMinutes(20));

    // when
    final var before = Instant.now();
    newStarter(loaderClient, starterProperties, new SimpleMeterRegistry()).run();
    final var after = Instant.now();

    // then
    final var timerStartAts = capturedVariables(loaderClient);
    assertThat(timerStartAts).hasSize(3);
    assertThat(timerStartAts)
        .describedAs("every instance gets the same value")
        .allSatisfy(variables -> assertThat(variables).containsKey("timerStartAt"))
        .extracting(variables -> variables.get("timerStartAt"))
        .containsOnly(timerStartAts.get(0).get("timerStartAt"));
    final var timerStartAt = Instant.parse((String) timerStartAts.get(0).get("timerStartAt"));
    assertThat(timerStartAt)
        .describedAs("the value is the start time plus the delay, truncated to seconds")
        .isBetween(
            before.plus(Duration.ofMinutes(20)).minusSeconds(1),
            after.plus(Duration.ofMinutes(20)));
    assertThat(timerStartAt.getNano()).isZero();
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldNotPassTimerStartAtWithoutTimerStartDelay() {
    // given
    final var loaderClient = mockLoaderClient();
    final var starterProperties = new StarterProperties();
    starterProperties.setInstanceLimit(1);

    // when
    newStarter(loaderClient, starterProperties, new SimpleMeterRegistry()).run();

    // then
    assertThat(capturedVariables(loaderClient))
        .allSatisfy(variables -> assertThat(variables).doesNotContainKey("timerStartAt"));
  }

  private static CamundaClient mockLoaderClient() {
    final var loaderClient = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    final var instance = mock(ProcessInstanceEvent.class);
    when(instance.getProcessInstanceKey()).thenReturn(1L);
    when(loaderClient
            .newCreateInstanceCommand()
            .bpmnProcessId(anyString())
            .latestVersion()
            .variables(any(Map.class))
            .send())
        .thenReturn(CompletedFuture.of(instance));
    return loaderClient;
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
