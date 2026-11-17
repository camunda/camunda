/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.client.api.command.TopologyRequestStep1;
import io.camunda.client.api.response.Topology;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.scheduling.TaskScheduler;

class ClusterConnectionTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final List<Object> events = new CopyOnWriteArrayList<>();
  private final TaskScheduler scheduler = mock(TaskScheduler.class);
  private ClusterConnection connection;

  @BeforeEach
  void runScheduledTasksImmediately() {
    when(scheduler.schedule(any(Runnable.class), any(Instant.class)))
        .thenAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(0).run();
              return null;
            });
  }

  @Test
  void shouldNotBeConnectedBeforeStart() {
    // given
    connection = newConnection(mock(CamundaClient.class));

    // then
    assertThat(registry.find(AppMetricsDoc.CONNECTED.getName()).gauge().value()).isZero();
    assertThat(connection.health().getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
  }

  @Test
  void shouldPublishConnectedAndFlipGaugeOnSuccessfulTopology() {
    // given
    connection = newConnection(clientAnswering(topologyFuture()));

    // when
    connection.start();
    connection.connect();

    // then
    assertThat(events).containsExactly(new Connected());
    assertThat(registry.find(AppMetricsDoc.CONNECTED.getName()).gauge().value()).isEqualTo(1.0);
    assertThat(connection.health().getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void shouldRetryAfterAuthenticationFailureUntilClusterAccepts() {
    // given
    final var rejected =
        CompletableFuture.<Topology>failedFuture(
            new ClientStatusException(io.grpc.Status.UNAUTHENTICATED, new RuntimeException()));
    connection = newConnection(clientAnswering(failing(rejected), topologyFuture()));

    // when
    connection.start();
    connection.connect();

    // then
    assertThat(connection.isConnected()).isTrue();
    assertThat(events).containsExactly(new Connected());
  }

  @Test
  void shouldNotRetryAfterStop() {
    // given
    final var pending = new CompletableFuture<Topology>();
    connection = newConnection(clientAnswering(failing(pending)));
    connection.start();
    connection.connect();

    // when
    connection.stop();
    pending.completeExceptionally(new RuntimeException("late failure"));

    // then
    assertThat(connection.isRunning()).isFalse();
    assertThat(events).isEmpty();
    verify(scheduler, never()).schedule(any(Runnable.class), any(Instant.class));
  }

  @Test
  void shouldGrowBackoffExponentiallyUpToTheMaximum() {
    // when
    final var first = ClusterConnection.backoff(0);
    final var later = ClusterConnection.backoff(30);

    // then
    assertThat(first)
        .isBetween(
            ClusterConnection.INITIAL_BACKOFF.dividedBy(2), ClusterConnection.INITIAL_BACKOFF);
    assertThat(later)
        .isBetween(ClusterConnection.MAX_BACKOFF.dividedBy(2), ClusterConnection.MAX_BACKOFF);
  }

  private ClusterConnection newConnection(final CamundaClient client) {
    return new ClusterConnection(client, registry, events::add, scheduler);
  }

  private static CamundaFuture<Topology> topologyFuture() {
    final var topology = mock(Topology.class);
    when(topology.getBrokers()).thenReturn(Collections.emptyList());
    return wrap(CompletableFuture.completedFuture(topology));
  }

  private static CamundaFuture<Topology> failing(final CompletableFuture<Topology> future) {
    return wrap(future);
  }

  @SuppressWarnings("unchecked")
  private static CamundaFuture<Topology> wrap(final CompletableFuture<Topology> future) {
    final CamundaFuture<Topology> wrapped = mock(CamundaFuture.class);
    when(wrapped.whenComplete(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            invocation -> {
              future.whenComplete(invocation.getArgument(0));
              return future;
            });
    return wrapped;
  }

  @SafeVarargs
  private static CamundaClient clientAnswering(final CamundaFuture<Topology>... futures) {
    final var request = mock(TopologyRequestStep1.class);
    when(request.send())
        .thenReturn(futures[0], java.util.Arrays.copyOfRange(futures, 1, futures.length));
    final var client = mock(CamundaClient.class);
    when(client.newTopologyRequest()).thenReturn(request);
    return client;
  }
}
