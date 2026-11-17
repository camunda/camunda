/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.Process;
import io.camunda.zeebe.config.LoadTesterProperties;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.scheduling.TaskScheduler;

class ProcessDeployerTest {

  private final List<Object> events = new CopyOnWriteArrayList<>();
  private final LoadTesterProperties properties = new LoadTesterProperties();

  @Test
  void shouldPublishProcessDeployedWithoutBlockingTheCaller() {
    // given
    final var pending = new CompletableFuture<DeploymentEvent>();
    final var deployer = newDeployer(deployFutures(pending), mock(TaskScheduler.class));

    // when
    deployer.onConnected();

    // then — the listener returned while the deployment is still pending
    assertThat(events).isEmpty();

    // and
    pending.complete(deployment(42L));
    assertThat(events).containsExactly(new ProcessDeployed(42L));
  }

  @Test
  void shouldRetryAfterFailureUntilTheDeploymentSucceeds() {
    // given — the first deployment fails, the retry succeeds
    final var failed = CompletableFuture.<DeploymentEvent>failedFuture(new RuntimeException());
    final var succeeded = CompletableFuture.completedFuture(deployment(7L));
    final var scheduler = mock(TaskScheduler.class);
    when(scheduler.schedule(any(Runnable.class), any(Instant.class)))
        .thenAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(0).run();
              return null;
            });
    final var deployer = newDeployer(deployFutures(failed, succeeded), scheduler);

    // when
    deployer.onConnected();

    // then
    assertThat(events).containsExactly(new ProcessDeployed(7L));
  }

  @Test
  void shouldBeReadyOnlyOnceTheProcessIsDeployed() {
    // given
    final var pending = new CompletableFuture<DeploymentEvent>();
    final var deployer = newDeployer(deployFutures(pending), mock(TaskScheduler.class));
    deployer.onConnected();
    assertThat(deployer.health().getStatus()).isEqualTo(Status.OUT_OF_SERVICE);

    // when
    pending.complete(deployment(42L));

    // then
    assertThat(deployer.health().getStatus()).isEqualTo(Status.UP);
  }

  private ProcessDeployer newDeployer(final CamundaClient client, final TaskScheduler scheduler) {
    return new ProcessDeployer(
        client, properties, mock(StarterLivenessIndicator.class), events::add, scheduler);
  }

  private DeploymentEvent deployment(final long processDefinitionKey) {
    final var process = mock(Process.class);
    when(process.getBpmnProcessId()).thenReturn(properties.getStarter().getProcessId());
    when(process.getProcessDefinitionKey()).thenReturn(processDefinitionKey);
    final var deployment = mock(DeploymentEvent.class);
    when(deployment.getProcesses()).thenReturn(List.of(process));
    return deployment;
  }

  @SafeVarargs
  private static CamundaClient deployFutures(final CompletableFuture<DeploymentEvent>... futures) {
    final var wrapped = Arrays.stream(futures).map(ProcessDeployerTest::asCamundaFuture).toList();
    final var client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    when(client.newDeployResourceCommand().addResourceFromClasspath(any()).send())
        .thenReturn(wrapped.get(0), wrapped.stream().skip(1).toArray(CamundaFuture[]::new));
    return client;
  }

  @SuppressWarnings("unchecked")
  private static CamundaFuture<DeploymentEvent> asCamundaFuture(
      final CompletableFuture<DeploymentEvent> future) {
    final CamundaFuture<DeploymentEvent> wrapped = mock(CamundaFuture.class);
    when(wrapped.whenComplete(any()))
        .thenAnswer(
            invocation -> {
              future.whenComplete(invocation.getArgument(0));
              return future;
            });
    return wrapped;
  }
}
