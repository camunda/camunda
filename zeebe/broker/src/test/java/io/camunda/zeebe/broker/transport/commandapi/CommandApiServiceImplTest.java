/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.system.configuration.QueryApiCfg;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.ServerTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CommandApiServiceImplTest {

  @Mock private ServerTransport serverTransport;
  @Mock private QueryApiCfg queryApi;
  private CommandApiServiceImpl commandApiService;

  @Mock(strictness = Strictness.LENIENT)
  private PartitionTransitionContext transitionContext;

  @Mock(strictness = Strictness.LENIENT)
  private LogStream logStream;

  @RegisterExtension
  private ControlledActorSchedulerExtension scheduler = new ControlledActorSchedulerExtension();

  @BeforeEach
  public void setup() {
    final ConcurrencyControl cc =
        mock(
            ConcurrencyControl.class,
            withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
    when(cc.createCompletedFuture()).thenReturn(CompletableActorFuture.completed(null));
    commandApiService =
        new CommandApiServiceImpl(1, serverTransport, scheduler.getActorScheduler(), queryApi);
    when(transitionContext.getCommandApiService()).thenReturn(commandApiService);
    when(transitionContext.getConcurrencyControl()).thenReturn(cc);
    scheduler.submitActor(commandApiService);
    scheduler.workUntilDone();
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"FOLLOWER", "CANDIDATE"})
  public void subscribesWhenBecomingLeader(final Role nonLeaderRole) {
    // given
    when(transitionContext.getPartitionId()).thenReturn(1);
    when(logStream.newLogStreamWriter()).thenReturn(mock());
    when(transitionContext.getLogStream()).thenReturn(logStream);
    when(transitionContext.getQueryService()).thenReturn(mock());
    final var transitionStep = new CommandApiServiceTransitionStep();
    final var prepareFuture = transitionStep.prepareTransition(transitionContext, 1L, Role.LEADER);
    scheduler.workUntilDone();
    prepareFuture.join();

    // when - becomes LEADER for the partition
    final var transitionFuture = transitionStep.transitionTo(transitionContext, 1, Role.LEADER);
    scheduler.workUntilDone();
    transitionFuture.join();

    // then
    verify(logStream, times(1)).newLogStreamWriter();
    verify(serverTransport, times(1)).subscribe(eq(1), eq(RequestType.QUERY), any());
    verify(serverTransport, times(1)).subscribe(eq(1), eq(RequestType.COMMAND), any());

    // when - transitions to follower/candidate: deactivate handlers but keep transport subscription
    final var prepareFollowerFuture =
        transitionStep.prepareTransition(transitionContext, 2, nonLeaderRole);
    scheduler.workUntilDone();
    prepareFollowerFuture.join();

    // then - transport subscription is kept
    verify(serverTransport, never()).unsubscribe(eq(1), eq(RequestType.QUERY));
    verify(serverTransport, never()).unsubscribe(eq(1), eq(RequestType.COMMAND));
  }

  @Test
  public void shouldUnsubscribeWhenTransitioningToInactive() {
    // given
    when(transitionContext.getPartitionId()).thenReturn(1);
    when(logStream.newLogStreamWriter()).thenReturn(mock());
    when(transitionContext.getLogStream()).thenReturn(logStream);
    when(transitionContext.getQueryService()).thenReturn(mock());
    final var transitionStep = new CommandApiServiceTransitionStep();
    final var prepareFuture = transitionStep.prepareTransition(transitionContext, 1L, Role.LEADER);
    scheduler.workUntilDone();
    prepareFuture.join();
    final var transitionFuture = transitionStep.transitionTo(transitionContext, 1, Role.LEADER);
    scheduler.workUntilDone();
    transitionFuture.join();
    clearInvocations(serverTransport);

    // when - transitions to INACTIVE
    final var prepareInactiveFuture =
        transitionStep.prepareTransition(transitionContext, 2, Role.INACTIVE);
    scheduler.workUntilDone();
    prepareInactiveFuture.join();

    // then - transport subscription is removed
    verify(serverTransport, times(1)).unsubscribe(eq(1), eq(RequestType.QUERY));
    verify(serverTransport, times(1)).unsubscribe(eq(1), eq(RequestType.COMMAND));
  }

  @RegressionTest("https://github.com/camunda/camunda/issues/25897")
  @Timeout(value = 10)
  public void shouldUnsubscribeOnceWhenTransitioningFromFollowerToInactive() {
    // given - partition transitions to FOLLOWER first (deactivate, no unsubscribe)
    when(transitionContext.getPartitionId()).thenReturn(1);
    final var transitionStep = new CommandApiServiceTransitionStep();
    final var prepareFollowerFuture =
        transitionStep.prepareTransition(transitionContext, 1, Role.FOLLOWER);
    scheduler.workUntilDone();
    prepareFollowerFuture.join();

    final var transitionFollowerFuture =
        transitionStep.transitionTo(transitionContext, 1, Role.FOLLOWER);
    scheduler.workUntilDone();
    transitionFollowerFuture.join();

    // then - no unsubscribe yet (kept subscription for graceful rejection)
    verify(serverTransport, never()).unsubscribe(eq(1), eq(RequestType.QUERY));
    verify(serverTransport, never()).unsubscribe(eq(1), eq(RequestType.COMMAND));

    // when - transitions to INACTIVE
    final var unregisterFuture =
        transitionStep.prepareTransition(transitionContext, 2, Role.INACTIVE);
    scheduler.workUntilDone();
    unregisterFuture.join();

    final var transitionInactiveFuture =
        transitionStep.transitionTo(transitionContext, 2, Role.INACTIVE);
    scheduler.workUntilDone();
    transitionInactiveFuture.join();

    // then - unsubscribed exactly once
    verify(serverTransport, times(1)).unsubscribe(eq(1), eq(RequestType.QUERY));
    verify(serverTransport, times(1)).unsubscribe(eq(1), eq(RequestType.COMMAND));
  }

  @RegressionTest("https://github.com/camunda/camunda/issues/25897")
  @Timeout(value = 10)
  public void shouldUnsubscribeOnActorClosing() {
    // given
    when(logStream.newLogStreamWriter()).thenReturn(mock());
    when(transitionContext.getQueryService()).thenReturn(mock());

    commandApiService.registerHandlers(1, logStream, transitionContext.getQueryService());
    scheduler.workUntilDone();

    verify(serverTransport, times(1)).subscribe(eq(1), eq(RequestType.QUERY), any());
    verify(serverTransport, times(1)).subscribe(eq(1), eq(RequestType.COMMAND), any());

    // when - closing the actor
    final ActorFuture<Void> closeFuture = commandApiService.closeAsync();
    scheduler.workUntilDone();
    closeFuture.join();

    // then - subscriptions are cleaned up
    verify(serverTransport, times(1)).unsubscribe(eq(1), eq(RequestType.QUERY));
    verify(serverTransport, times(1)).unsubscribe(eq(1), eq(RequestType.COMMAND));
  }
}
