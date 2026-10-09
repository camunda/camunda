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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
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
