/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.clustering.ClusterServicesImpl;
import io.camunda.zeebe.broker.system.management.BrokerAdminServiceImpl;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.util.sched.ActorSchedulingService;
import io.camunda.zeebe.util.sched.TestConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

final class BrokerAdminServiceStepTest {
  private static final TestConcurrencyControl CONCURRENCY_CONTROL = new TestConcurrencyControl();
  private static final Duration TIME_OUT = Duration.ofSeconds(10);

  private BrokerStartupContext mockBrokerStartupContext;
  private ActorSchedulingService mockActorSchedulingService;
  private SpringBrokerBridge mockSpringBrokerBridge;
  private BrokerAdminServiceImpl mockBrokerAdminService;

  private ActorFuture<BrokerStartupContext> future;

  private final BrokerAdminServiceStep sut = new BrokerAdminServiceStep();

  @BeforeEach
  void setUp() {
    mockActorSchedulingService = mock(ActorSchedulingService.class);

    when(mockActorSchedulingService.submitActor(any()))
        .thenReturn(CONCURRENCY_CONTROL.completedFuture(null));

    mockSpringBrokerBridge = mock(SpringBrokerBridge.class);

    mockBrokerAdminService = mock(BrokerAdminServiceImpl.class);
    when(mockBrokerAdminService.closeAsync()).thenReturn(CONCURRENCY_CONTROL.completedFuture(null));

    mockBrokerStartupContext = mock(BrokerStartupContext.class);

    when(mockBrokerStartupContext.getConcurrencyControl()).thenReturn(CONCURRENCY_CONTROL);
    when(mockBrokerStartupContext.getBrokerInfo()).thenReturn(mock(BrokerInfo.class));
    when(mockBrokerStartupContext.getActorSchedulingService())
        .thenReturn(mockActorSchedulingService);
    when(mockBrokerStartupContext.getSpringBrokerBridge()).thenReturn(mockSpringBrokerBridge);
    when(mockBrokerStartupContext.getBrokerAdminService()).thenReturn(mockBrokerAdminService);

    when(mockBrokerStartupContext.getClusterServices())
        .thenReturn(mock(ClusterServicesImpl.class, Mockito.RETURNS_DEEP_STUBS));

    future = CONCURRENCY_CONTROL.createFuture();
  }

  @Test
  void shouldCompleteFutureOnStartup() {
    // when
    sut.startupInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);

    // then
    assertThat(future).succeedsWithin(TIME_OUT);
    assertThat(future.join()).isNotNull();
  }

  @Test
  void shouldScheduleBrokerAdminServiceOnStartup() {
    // when
    sut.startupInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);
    await().until(future::isDone);

    // then
    final var argumentCaptor = ArgumentCaptor.forClass(BrokerAdminServiceImpl.class);
    verify(mockActorSchedulingService).submitActor(argumentCaptor.capture());

    verify(mockBrokerStartupContext).setBrokerAdminService(argumentCaptor.getValue());
  }

  @Test
  void shouldRegisterBrokerAdminServiceInSpringBrokerBridgeOnStartup() {
    // when
    sut.startupInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);
    await().until(future::isDone);

    // then
    final var adminServiceCaptor = ArgumentCaptor.forClass(BrokerAdminServiceImpl.class);
    verify(mockBrokerStartupContext).setBrokerAdminService(adminServiceCaptor.capture());

    final var adminServiceSupplierCaptor = ArgumentCaptor.forClass(Supplier.class);
    verify(mockSpringBrokerBridge)
        .registerBrokerAdminServiceSupplier(adminServiceSupplierCaptor.capture());

    assertThat(adminServiceSupplierCaptor.getValue().get()).isSameAs(adminServiceCaptor.getValue());
  }

  @Test
  void shouldCompleteFutureOnShutdown() {
    // when
    sut.shutdownInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);

    // then
    assertThat(future).succeedsWithin(TIME_OUT);
    assertThat(future.join()).isNotNull();
  }

  @Test
  void shouldStopBrokerAdminServiceOnShutdown() {
    // when
    sut.shutdownInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);
    await().until(future::isDone);

    // then
    verify(mockBrokerAdminService).closeAsync();
    verify(mockBrokerStartupContext).setBrokerAdminService(null);
  }
}
