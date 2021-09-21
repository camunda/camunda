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
import io.camunda.zeebe.broker.system.management.LeaderManagementRequestHandler;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.util.sched.ActorSchedulingService;
import io.camunda.zeebe.util.sched.TestConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class LeaderManagementRequestHandlerStepTest {
  private static final TestConcurrencyControl CONCURRENCY_CONTROL = new TestConcurrencyControl();
  private static final Duration TIME_OUT = Duration.ofSeconds(10);

  private BrokerStartupContext mockBrokerStartupContext;
  private ActorSchedulingService mockActorSchedulingService;
  private LeaderManagementRequestHandler mockLeaderManagementRequestHandler;

  private ActorFuture<BrokerStartupContext> future;

  private final LeaderManagementRequestHandlerStep sut = new LeaderManagementRequestHandlerStep();

  @BeforeEach
  void setUp() {
    mockActorSchedulingService = mock(ActorSchedulingService.class);

    when(mockActorSchedulingService.submitActor(any()))
        .thenReturn(CONCURRENCY_CONTROL.completedFuture(null));

    mockLeaderManagementRequestHandler = mock(LeaderManagementRequestHandler.class);
    when(mockLeaderManagementRequestHandler.closeAsync())
        .thenReturn(CONCURRENCY_CONTROL.completedFuture(null));

    mockBrokerStartupContext = mock(BrokerStartupContext.class);

    when(mockBrokerStartupContext.getConcurrencyControl()).thenReturn(CONCURRENCY_CONTROL);
    when(mockBrokerStartupContext.getBrokerInfo()).thenReturn(mock(BrokerInfo.class));
    when(mockBrokerStartupContext.getActorSchedulingService())
        .thenReturn(mockActorSchedulingService);
    when(mockBrokerStartupContext.getLeaderManagementRequestHandler())
        .thenReturn(mockLeaderManagementRequestHandler);

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
  void shouldScheduleLeaderManagementRequestHandlerActorOnStartup() {
    // when
    sut.startupInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);
    await().until(future::isDone);

    // then
    final var argumentCaptor = ArgumentCaptor.forClass(LeaderManagementRequestHandler.class);
    verify(mockActorSchedulingService).submitActor(argumentCaptor.capture());
    verify(mockBrokerStartupContext).setLeaderManagementRequestHandler(argumentCaptor.getValue());
  }

  @Test
  void shouldRegisterLeaderRequestManagementHandlerAsPartitionListenerOnStartup() {
    // when
    sut.startupInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);
    await().until(future::isDone);

    // then
    final var argumentCaptor = ArgumentCaptor.forClass(LeaderManagementRequestHandler.class);
    verify(mockBrokerStartupContext).setLeaderManagementRequestHandler(argumentCaptor.capture());
    verify(mockBrokerStartupContext).addPartitionListener(argumentCaptor.getValue());
  }

  @Test
  void shouldRegisterLeaderRequestManagementHandlerAsDiskSpaceListenerOnStartup() {
    // when
    sut.startupInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);
    await().until(future::isDone);

    // then
    final var argumentCaptor = ArgumentCaptor.forClass(LeaderManagementRequestHandler.class);
    verify(mockBrokerStartupContext).setLeaderManagementRequestHandler(argumentCaptor.capture());
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
  void shouldStopHealthCheckServiceOnShutdown() {
    // when
    sut.shutdownInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);
    await().until(future::isDone);

    // then
    verify(mockLeaderManagementRequestHandler).closeAsync();
    verify(mockBrokerStartupContext).setLeaderManagementRequestHandler(null);
  }

  @Test
  void shouldRemoveLeaderRequestManagementHandlerAsPartitionListenerOnStartup() {
    // when
    sut.shutdownInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);
    await().until(future::isDone);

    // then
    verify(mockBrokerStartupContext).removePartitionListener(mockLeaderManagementRequestHandler);
  }

  @Test
  void shouldRemoveLeaderRequestManagementHandlerAsDiskSpaceListenerOnStartup() {
    // when
    sut.shutdownInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);
    await().until(future::isDone);

    // then
    verify(mockBrokerStartupContext)
        .removeDiskSpaceUsageListener(mockLeaderManagementRequestHandler);
  }
}
