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

import io.camunda.zeebe.broker.clustering.ClusterServicesImpl;
import io.camunda.zeebe.broker.engine.impl.SubscriptionApiCommandMessageHandlerService;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.util.sched.ActorSchedulingService;
import io.camunda.zeebe.util.sched.TestConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SubscriptionApiStepTest {
  private static final TestConcurrencyControl CONCURRENCY_CONTROL = new TestConcurrencyControl();
  private static final Duration TIME_OUT = Duration.ofSeconds(10);

  private BrokerStartupContext mockBrokerStartupContext;
  private SubscriptionApiCommandMessageHandlerService mockSubscriptionApiService;
  private ActorSchedulingService mockActorSchedulingService;

  private ActorFuture<BrokerStartupContext> future;

  private final SubscriptionApiStep sut = new SubscriptionApiStep();

  @BeforeEach
  void setUp() {
    mockBrokerStartupContext = mock(BrokerStartupContext.class);
    mockActorSchedulingService = mock(ActorSchedulingService.class);

    mockSubscriptionApiService = mock(SubscriptionApiCommandMessageHandlerService.class);
    when(mockSubscriptionApiService.closeAsync())
        .thenReturn(CONCURRENCY_CONTROL.completedFuture(null));

    when(mockBrokerStartupContext.getConcurrencyControl()).thenReturn(CONCURRENCY_CONTROL);
    when(mockBrokerStartupContext.getSubscriptionApiService())
        .thenReturn(mockSubscriptionApiService);
    when(mockBrokerStartupContext.getActorSchedulingService())
        .thenReturn(mockActorSchedulingService);
    when(mockBrokerStartupContext.getClusterServices()).thenReturn(mock(ClusterServicesImpl.class));
    when(mockBrokerStartupContext.getBrokerInfo()).thenReturn(mock(BrokerInfo.class));

    when(mockActorSchedulingService.submitActor(any()))
        .thenReturn(CONCURRENCY_CONTROL.completedFuture(null));

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
  void shouldScheduleSubscriptionApiOnStartup() {
    // when
    sut.startupInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);
    await().until(future::isDone);

    // then
    final var argumentCaptor =
        ArgumentCaptor.forClass(SubscriptionApiCommandMessageHandlerService.class);
    verify(mockBrokerStartupContext).setSubscriptionApiService(argumentCaptor.capture());
    verify(mockActorSchedulingService).submitActor(argumentCaptor.getValue());
  }

  @Test
  void shouldRegisterSubscriptionApiAsPartitionListenerOnStartup() {
    // when
    sut.startupInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);
    await().until(future::isDone);

    // then
    final var argumentCaptor =
        ArgumentCaptor.forClass(SubscriptionApiCommandMessageHandlerService.class);
    verify(mockBrokerStartupContext).setSubscriptionApiService(argumentCaptor.capture());
    verify(mockBrokerStartupContext).addPartitionListener(argumentCaptor.getValue());
  }

  @Test
  void shouldRegisterSubscriptionApiAsDiskSpaceUsageListenerOnStartup() {
    // when
    sut.startupInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);
    await().until(future::isDone);

    // then
    final var argumentCaptor =
        ArgumentCaptor.forClass(SubscriptionApiCommandMessageHandlerService.class);
    verify(mockBrokerStartupContext).setSubscriptionApiService(argumentCaptor.capture());
    verify(mockBrokerStartupContext).addDiskSpaceUsageListener(argumentCaptor.getValue());
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
  void shouldStopSubscriptionApiOnShutdown() {
    // when
    sut.shutdownInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);
    await().until(future::isDone);

    // then
    verify(mockSubscriptionApiService).closeAsync();
    verify(mockBrokerStartupContext).setSubscriptionApiService(null);
  }

  @Test
  void shouldUnregisterSubscriptionApiAsPartitionListenerOnShutdown() {
    // when
    sut.shutdownInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);
    await().until(future::isDone);

    // then
    verify(mockBrokerStartupContext).removePartitionListener(mockSubscriptionApiService);
  }

  @Test
  void shouldUnregisterSubscriptionApiAsDiskSpaceUsageListenerOnShutdown() {
    // when
    sut.shutdownInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);
    await().until(future::isDone);

    // then
    verify(mockBrokerStartupContext).removeDiskSpaceUsageListener(mockSubscriptionApiService);
  }
}
