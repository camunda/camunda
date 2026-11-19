/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import static io.camunda.zeebe.broker.partitioning.PartitionManager.DEFAULT_GROUP_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.hub.HubClient;
import io.camunda.zeebe.broker.hub.HubClientFactory;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class HubUpdateReceiverStepTest {

  private static final TestConcurrencyControl CONCURRENCY_CONTROL = new TestConcurrencyControl();
  private static final String HUB_MANAGED_TENANT = "hub-managed";

  private final List<CompletableActorFuture<Void>> startedActors = new ArrayList<>();
  private final ActorSchedulingService actorScheduler = mock(ActorSchedulingService.class);
  private final HubUpdateReceiverStep sut = new HubUpdateReceiverStep();
  private MockBrokerStartupContext ctx;
  private ActorFuture<BrokerStartupContext> startupFuture;

  @BeforeEach
  void setUp() {
    startupFuture = CONCURRENCY_CONTROL.createFuture();
    ctx = new MockBrokerStartupContext();
    ctx.setConcurrencyControl(CONCURRENCY_CONTROL);
    ctx.setPhysicalTenantIds(() -> Set.of(DEFAULT_GROUP_NAME, HUB_MANAGED_TENANT));
    when(actorScheduler.submitActor(any()))
        .thenAnswer(
            invocation -> {
              final var started = new CompletableActorFuture<Void>();
              startedActors.add(started);
              return started;
            });
    ctx.setActorSchedulingService(actorScheduler);
  }

  @Test
  void shouldStartNoReceiverWithoutAHubClientFactory() {
    // given
    when(ctx.getSpringBrokerBridge().getHubClientFactory()).thenReturn(Optional.empty());

    // when
    sut.startupInternal(ctx, CONCURRENCY_CONTROL, startupFuture);

    // then
    assertThat(startupFuture.isDone()).isTrue();
    assertThat(startupFuture.isCompletedExceptionally()).isFalse();
    verify(actorScheduler, never()).submitActor(any());
    assertThat(ctx.getHubUpdateReceiver(DEFAULT_GROUP_NAME)).isNull();
  }

  @Test
  void shouldStartAReceiverOnlyForTenantsWithAHubClient() {
    // given
    final HubClientFactory factory =
        tenantId ->
            tenantId.equals(HUB_MANAGED_TENANT)
                ? Optional.of(mock(HubClient.class))
                : Optional.empty();
    when(ctx.getSpringBrokerBridge().getHubClientFactory()).thenReturn(Optional.of(factory));

    // when
    sut.startupInternal(ctx, CONCURRENCY_CONTROL, startupFuture);
    startedActors.forEach(started -> started.complete(null));

    // then
    assertThat(startupFuture.isDone()).isTrue();
    assertThat(startupFuture.isCompletedExceptionally()).isFalse();
    assertThat(ctx.getHubUpdateReceiver(HUB_MANAGED_TENANT)).isNotNull();
    assertThat(ctx.getHubUpdateReceiver(DEFAULT_GROUP_NAME)).isNull();
  }

  @Test
  void shouldRemoveTheReceiversOnShutdown() {
    // given
    when(ctx.getSpringBrokerBridge().getHubClientFactory())
        .thenReturn(Optional.of(tenantId -> Optional.of(mock(HubClient.class))));
    sut.startupInternal(ctx, CONCURRENCY_CONTROL, startupFuture);
    startedActors.forEach(started -> started.complete(null));
    final ActorFuture<BrokerStartupContext> shutdownFuture = CONCURRENCY_CONTROL.createFuture();

    // when
    sut.shutdownInternal(ctx, CONCURRENCY_CONTROL, shutdownFuture);

    // then
    assertThat(ctx.getHubUpdateReceiver(DEFAULT_GROUP_NAME)).isNull();
    assertThat(ctx.getHubUpdateReceiver(HUB_MANAGED_TENANT)).isNull();
  }
}
