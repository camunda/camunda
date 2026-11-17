/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.Process;
import io.camunda.zeebe.config.LoadTesterProperties;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ProcessDeployerTest {

  private final List<Object> events = new CopyOnWriteArrayList<>();
  private ProcessDeployer deployer;

  @AfterEach
  void tearDown() {
    deployer.stop();
  }

  @Test
  void shouldDeployOnConnectedWithoutBlockingTheCallerAndPublishProcessDeployed() {
    // given
    final var properties = new LoadTesterProperties();
    final var process = mock(Process.class);
    when(process.getBpmnProcessId()).thenReturn(properties.getStarter().getProcessId());
    when(process.getProcessDefinitionKey()).thenReturn(42L);
    final var deployment = mock(DeploymentEvent.class);
    when(deployment.getProcesses()).thenReturn(List.of(process));
    final var pending = new CompletableFuture<DeploymentEvent>();
    final var pendingFuture = asCamundaFuture(pending);
    final var client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    when(client
            .newDeployResourceCommand()
            .addResourceFromClasspath(org.mockito.ArgumentMatchers.any())
            .send())
        .thenReturn(pendingFuture);
    final var liveness = mock(StarterLivenessIndicator.class);
    deployer = new ProcessDeployer(client, properties, liveness, events::add);

    // when
    deployer.onConnected();

    // then — the listener returned while the deployment is still pending
    verify(liveness).recordStarted();
    assertThat(events).isEmpty();

    // and
    pending.complete(deployment);
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(events).containsExactly(new ProcessDeployed(42L)));
  }

  @SuppressWarnings("unchecked")
  private static CamundaFuture<DeploymentEvent> asCamundaFuture(
      final CompletableFuture<DeploymentEvent> future) {
    final CamundaFuture<DeploymentEvent> wrapped = mock(CamundaFuture.class);
    when(wrapped.join()).thenAnswer(invocation -> future.join());
    return wrapped;
  }
}
