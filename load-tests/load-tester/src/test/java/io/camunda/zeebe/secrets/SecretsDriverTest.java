/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ResolveSecretsResponse;
import io.camunda.zeebe.config.LoadTesterProperties;
import io.camunda.zeebe.config.ZeebeSecretsDriverProperties;
import io.camunda.zeebe.metrics.ConnectionMonitor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class SecretsDriverTest {

  private static final String LATENCY_TIMER = "zeebe.secrets.request.latency";

  @Test
  void shouldBuildBatchWithExactDuplicateAndDistinctCounts() {
    // given
    final var driver =
        newDriver(config(props -> props.setDuplicateRatio(0.3)), new SimpleMeterRegistry());

    // when
    final List<String> batch = driver.buildResolveBatch();

    // then: batchSize=10, duplicates=round(10*0.3)=3, distinct=7 (one distinct reference repeated)
    assertThat(batch).hasSize(10);
    assertThat(new HashSet<>(batch)).hasSize(7);
  }

  @Test
  void shouldBuildBatchWithoutDuplicatesWhenRatioIsZero() {
    // given
    final var driver =
        newDriver(config(props -> props.setDuplicateRatio(0.0)), new SimpleMeterRegistry());

    // when
    final List<String> batch = driver.buildResolveBatch();

    // then
    assertThat(batch).hasSize(10);
    assertThat(new HashSet<>(batch)).hasSize(10);
  }

  @Test
  void shouldRecordFullyResolvedResponseAsSuccess() {
    // given
    final var registry = new SimpleMeterRegistry();
    final var driver = newDriver(config(props -> {}), registry);
    driver.registerLatencyTimers();
    final ResolveSecretsResponse response = mock(ResolveSecretsResponse.class);
    when(response.isFullyResolved()).thenReturn(true);

    // when
    driver.recordOnComplete(
        CompletableFuture.completedFuture(response),
        "resolve",
        System.nanoTime(),
        SecretsDriver::isResolveSuccess);

    // then
    assertThat(latencyCount(registry, "resolve", "success")).isEqualTo(1L);
    assertThat(latencyCount(registry, "resolve", "error")).isZero();
  }

  @Test
  void shouldRecordPartiallyResolvedResponseAsError() {
    // given: per-reference failures are returned as response data, not exceptions, so the driver
    // must inspect isFullyResolved() rather than treating any non-exceptional completion as success
    final var registry = new SimpleMeterRegistry();
    final var driver = newDriver(config(props -> {}), registry);
    driver.registerLatencyTimers();
    final ResolveSecretsResponse response = mock(ResolveSecretsResponse.class);
    when(response.isFullyResolved()).thenReturn(false);

    // when
    driver.recordOnComplete(
        CompletableFuture.completedFuture(response),
        "resolve",
        System.nanoTime(),
        SecretsDriver::isResolveSuccess);

    // then
    assertThat(latencyCount(registry, "resolve", "error")).isEqualTo(1L);
    assertThat(latencyCount(registry, "resolve", "success")).isZero();
  }

  @Test
  void shouldRecordFailedFutureAsError() {
    // given
    final var registry = new SimpleMeterRegistry();
    final var driver = newDriver(config(props -> {}), registry);
    driver.registerLatencyTimers();
    final CompletableFuture<ResolveSecretsResponse> failed = new CompletableFuture<>();
    failed.completeExceptionally(new RuntimeException("boom"));

    // when
    driver.recordOnComplete(failed, "resolve", System.nanoTime(), SecretsDriver::isResolveSuccess);

    // then
    assertThat(latencyCount(registry, "resolve", "error")).isEqualTo(1L);
    assertThat(latencyCount(registry, "resolve", "success")).isZero();
  }

  private long latencyCount(
      final SimpleMeterRegistry registry, final String endpoint, final String outcome) {
    return registry
        .get(LATENCY_TIMER)
        .tags("endpoint", endpoint, "outcome", outcome)
        .timer()
        .count();
  }

  private ZeebeSecretsDriverProperties config(
      final Consumer<ZeebeSecretsDriverProperties> customizer) {
    final var props = new ZeebeSecretsDriverProperties();
    props.setBatchSize(10);
    props.setReferencePoolSize(100);
    customizer.accept(props);
    return props;
  }

  private SecretsDriver newDriver(
      final ZeebeSecretsDriverProperties config, final SimpleMeterRegistry registry) {
    final var properties = new LoadTesterProperties();
    properties.setZeebeSecrets(config);
    return new SecretsDriver(
        mock(CamundaClient.class), properties, registry, mock(ConnectionMonitor.class));
  }
}
