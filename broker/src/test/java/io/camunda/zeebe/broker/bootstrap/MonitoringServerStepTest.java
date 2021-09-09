/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.util.sched.TestConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class MonitoringServerStepTest {
  private static final TestConcurrencyControl CONCURRENCY_CONTROL = new TestConcurrencyControl();

  private SpringBrokerBridge mockSpringBrokerBridge;
  private BrokerStartupContext mockBrokerStartupContext;
  private BrokerHealthCheckService mockHealthCheckService;

  private ActorFuture<BrokerStartupContext> future;

  private final MonitoringServerStep sut = new MonitoringServerStep();

  @BeforeEach
  void setUp() {
    mockSpringBrokerBridge = mock(SpringBrokerBridge.class);
    mockBrokerStartupContext = mock(BrokerStartupContext.class);

    mockHealthCheckService = mock(BrokerHealthCheckService.class);
    when(mockHealthCheckService.closeAsync()).thenReturn(CONCURRENCY_CONTROL.completedFuture(null));

    when(mockBrokerStartupContext.getConcurrencyControl()).thenReturn(CONCURRENCY_CONTROL);
    when(mockBrokerStartupContext.getSpringBrokerBridge()).thenReturn(mockSpringBrokerBridge);
    when(mockBrokerStartupContext.getHealthCheckService()).thenReturn(mockHealthCheckService);

    when(mockBrokerStartupContext.scheduleActor(Mockito.any()))
        .thenReturn(CONCURRENCY_CONTROL.completedFuture(null));

    future = CONCURRENCY_CONTROL.createFuture();
  }

  @Test
  void shouldCompleteFutureOnStartup() {
    // when
    sut.startupInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);

    // then
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCompletedExceptionally()).isFalse();
  }

  @Test
  void shouldScheduleHealthMonitorActorOnStartup() {
    // when
    sut.startupInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);

    // then
    verify(mockBrokerStartupContext).scheduleActor(mockHealthCheckService);
  }

  @Test
  void shouldRegisterHealthMonitorAsPartitionListenerOnStartup() {
    // when
    sut.startupInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);

    // then
    verify(mockBrokerStartupContext).addPartitionListener(mockHealthCheckService);
  }

  @Test
  void shouldRegisterHealthMonitorInSpringBrokerBridgeOnStartup() {
    // when
    sut.startupInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);

    // then
    final var argumentCaptor = ArgumentCaptor.forClass(Supplier.class);
    verify(mockSpringBrokerBridge)
        .registerBrokerHealthCheckServiceSupplier(argumentCaptor.capture());

    assertThat(argumentCaptor.getValue().get()).isSameAs(mockHealthCheckService);
  }

  @Test
  void shouldCompleteFutureOnShutdown() {
    // when
    sut.shutdownInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);

    // then
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCompletedExceptionally()).isFalse();
  }

  @Test
  void shouldStopHealthCheckServiceOnShutdown() {
    // when
    sut.shutdownInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);

    // then
    verify(mockHealthCheckService).closeAsync();
  }

  @Test
  void shouldUnregisterHealthCheckServiceAsPartitionListenerOnShutdown() {
    // when
    sut.shutdownInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);

    // then
    verify(mockBrokerStartupContext).removePartitionListener(mockHealthCheckService);
  }

  @Test
  void shouldUnregisterHealthMonitorInSpringBrokerBridgeOnStartup() {
    // when
    sut.shutdownInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);

    // then
    final var argumentCaptor = ArgumentCaptor.forClass(Supplier.class);
    verify(mockSpringBrokerBridge)
        .registerBrokerHealthCheckServiceSupplier(argumentCaptor.capture());

    assertThat(argumentCaptor.getValue().get()).isNull();
  }
}
