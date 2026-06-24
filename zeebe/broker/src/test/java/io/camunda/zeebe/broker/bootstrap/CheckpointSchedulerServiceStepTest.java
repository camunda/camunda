/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.clustering.ClusterServicesImpl;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.management.CheckpointSchedulingService;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class CheckpointSchedulerServiceStepTest {
  private static final TestConcurrencyControl CONCURRENCY_CONTROL = new TestConcurrencyControl();
  private static final BrokerCfg TEST_BROKER_CONFIG = new BrokerCfg();
  private static final Duration TIME_OUT = Duration.ofSeconds(10);

  private BrokerStartupContext mockBrokerStartupContext;
  private CheckpointSchedulingService mockCheckpointSchedulingService;

  private ActorFuture<BrokerStartupContext> future;

  private final CheckpointSchedulerServiceStep sut = new CheckpointSchedulerServiceStep();

  @BeforeEach
  void setUp() {
    mockBrokerStartupContext = mock(BrokerStartupContext.class);
    final ActorSchedulingService mockActorSchedulingService = mock(ActorSchedulingService.class);
    final ClusterMembershipService mockMembershipService = mock(ClusterMembershipService.class);
    mockCheckpointSchedulingService = mock(CheckpointSchedulingService.class);
    when(mockCheckpointSchedulingService.closeAsync())
        .thenReturn(CONCURRENCY_CONTROL.completedFuture(null));
    final var mockClusterServices = mock(ClusterServicesImpl.class);

    when(mockBrokerStartupContext.getBrokerConfiguration()).thenReturn(TEST_BROKER_CONFIG);
    when(mockBrokerStartupContext.getConcurrencyControl()).thenReturn(CONCURRENCY_CONTROL);
    when(mockBrokerStartupContext.getActorSchedulingService())
        .thenReturn(mockActorSchedulingService);
    when(mockBrokerStartupContext.getClusterServices()).thenReturn(mockClusterServices);
    when(mockBrokerStartupContext.getCheckpointSchedulingService())
        .thenReturn(mockCheckpointSchedulingService);
    when(mockBrokerStartupContext.getBrokerClient()).thenReturn(mock(BrokerClient.class));

    final Member member = mock(Member.class);
    when(mockClusterServices.getMembershipService()).thenReturn(mockMembershipService);
    when(mockMembershipService.getMembers()).thenReturn(Set.of(member));
    when(mockMembershipService.getLocalMember()).thenReturn(member);

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
    final var argumentCaptor = ArgumentCaptor.forClass(CheckpointSchedulingService.class);
    verify(mockBrokerStartupContext).setCheckpointSchedulingService(argumentCaptor.capture());
  }

  @Test
  void shouldStopSchedulingServiceOnShutdown() {
    // when
    sut.shutdownInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);
    await().until(future::isDone);

    // then
    assertThat(future).succeedsWithin(TIME_OUT);
    assertThat(future.join()).isNotNull();
    verify(mockCheckpointSchedulingService).closeAsync();
    verify(mockBrokerStartupContext).setCheckpointSchedulingService(null);
  }
}
