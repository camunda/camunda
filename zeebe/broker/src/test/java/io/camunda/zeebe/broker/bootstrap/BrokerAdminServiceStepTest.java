/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.clustering.ClusterServicesImpl;
import io.camunda.zeebe.broker.partitioning.PartitionManager;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

final class BrokerAdminServiceStepTest {
  private static final TestConcurrencyControl CONCURRENCY_CONTROL = new TestConcurrencyControl();
  private static final Duration TIME_OUT = Duration.ofSeconds(10);

  private final List<CompletableActorFuture<Void>> scheduledActorFutures = new ArrayList<>();
  private MockBrokerStartupContext ctx;
  private ActorFuture<BrokerStartupContext> future;

  private final BrokerAdminServiceStep sut = new BrokerAdminServiceStep();

  @BeforeEach
  void setUp() {
    scheduledActorFutures.clear();
    ctx = new MockBrokerStartupContext();
    ctx.setConcurrencyControl(CONCURRENCY_CONTROL);
    ctx.addPartitionManager(DEFAULT_PHYSICAL_TENANT_ID, mock(PartitionManager.class));

    final var mockScheduler = mock(ActorSchedulingService.class);
    when(mockScheduler.submitActor(any()))
        .thenAnswer(
            inv -> {
              final var f = new CompletableActorFuture<Void>();
              scheduledActorFutures.add(f);
              return f;
            });
    ctx.setActorSchedulingService(mockScheduler);
    ctx.setClusterServices(mock(ClusterServicesImpl.class, Mockito.RETURNS_DEEP_STUBS));

    future = CONCURRENCY_CONTROL.createFuture();
  }

  private void completeAllScheduledActors() {
    for (int i = 0; i < scheduledActorFutures.size(); i++) {
      final var f = scheduledActorFutures.get(i);
      if (!f.isDone()) {
        f.complete(null);
      }
    }
  }

  @Test
  void shouldCompleteFutureOnStartup() {
    // when
    sut.startupInternal(ctx, CONCURRENCY_CONTROL, future);
    completeAllScheduledActors();

    // then
    assertThat(future).succeedsWithin(TIME_OUT);
    assertThat(future.join()).isNotNull();
  }

  @Test
  void shouldRegisterBrokerAdminServiceForDefaultTenantOnStartup() {
    // when
    sut.startupInternal(ctx, CONCURRENCY_CONTROL, future);
    completeAllScheduledActors();

    // then
    assertThat(future).succeedsWithin(TIME_OUT);
    assertThat(ctx.getBrokerAdminService(DEFAULT_PHYSICAL_TENANT_ID)).isNotNull();
  }

  @Test
  void shouldCreateDistinctServicesForEachPhysicalTenant() {
    // given
    final var secondTenantId = "second";
    ctx.setPhysicalTenantIds(() -> Set.of(DEFAULT_PHYSICAL_TENANT_ID, secondTenantId));
    ctx.addPartitionManager(secondTenantId, mock(PartitionManager.class));

    // when
    sut.startupInternal(ctx, CONCURRENCY_CONTROL, future);
    completeAllScheduledActors();

    // then
    assertThat(future).succeedsWithin(TIME_OUT);
    final var defaultService = ctx.getBrokerAdminService(DEFAULT_PHYSICAL_TENANT_ID);
    final var secondService = ctx.getBrokerAdminService(secondTenantId);
    assertThat(defaultService).isNotNull();
    assertThat(secondService).isNotNull();
    assertThat(defaultService).isNotSameAs(secondService);
  }

  @Test
  void shouldRegisterAdminServiceSupplierAndByTenantLookupWithSpringBrokerBridge() {
    // when
    sut.startupInternal(ctx, CONCURRENCY_CONTROL, future);
    completeAllScheduledActors();

    // then
    assertThat(future).succeedsWithin(TIME_OUT);
    verify(ctx.getSpringBrokerBridge()).registerBrokerAdminServiceSupplier(notNull());
    verify(ctx.getSpringBrokerBridge()).registerBrokerAdminServiceByTenantLookup(notNull());
  }

  @Test
  void shouldCompleteFutureOnShutdown() {
    // when
    sut.shutdownInternal(ctx, CONCURRENCY_CONTROL, future);

    // then
    assertThat(future).succeedsWithin(TIME_OUT);
    assertThat(future.join()).isNotNull();
  }

  @Test
  void shouldStopBrokerAdminServiceOnShutdown() {
    // given
    sut.startupInternal(ctx, CONCURRENCY_CONTROL, future);
    completeAllScheduledActors();
    assertThat(future).succeedsWithin(TIME_OUT);

    // when
    final var shutdownFuture = CONCURRENCY_CONTROL.<BrokerStartupContext>createFuture();
    sut.shutdownInternal(ctx, CONCURRENCY_CONTROL, shutdownFuture);

    // then
    assertThat(shutdownFuture).succeedsWithin(TIME_OUT);
    assertThat(ctx.getBrokerAdminService(DEFAULT_PHYSICAL_TENANT_ID)).isNull();
  }
}
