/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import io.camunda.zeebe.broker.transport.backpressure.PartitionAwareRequestLimiter;
import io.camunda.zeebe.broker.transport.backpressure.RequestLimiter;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.ServerTransport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
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
  private final ControlledActorSchedulerExtension scheduler =
      new ControlledActorSchedulerExtension();

  @BeforeEach
  public void setup() {
    final ConcurrencyControl cc =
        mock(
            ConcurrencyControl.class,
            withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
    when(cc.createCompletedFuture()).thenReturn(CompletableActorFuture.completed(null));

    final RequestLimiter<Intent> requestLimiter = mock();

    final PartitionAwareRequestLimiter limiter =
        mock(withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
    when(limiter.getLimiter(anyInt())).thenReturn(requestLimiter);

    commandApiService =
        new CommandApiServiceImpl(
            serverTransport,
            limiter,
            scheduler.getActorScheduler(),
            queryApi,
            new SimpleMeterRegistry());
    when(transitionContext.getCommandApiService()).thenReturn(commandApiService);
    when(transitionContext.getConcurrencyControl()).thenReturn(cc);
    scheduler.submitActor(commandApiService);
    scheduler.workUntilDone();
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"FOLLOWER", "CANDIDATE", "INACTIVE"})
  public void subscribesWhenBecomingLeader(final Role nonLeaderRole) {
    // given
    when(transitionContext.getPartitionId()).thenReturn(1);
    when(logStream.newLogStreamWriter()).thenReturn(CompletableActorFuture.completed(mock()));
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

    // when - become not a leader, subscriptions are cleaned up
    final var prepareFollowerFuture =
        transitionStep.prepareTransition(transitionContext, 2, nonLeaderRole);
    scheduler.workUntilDone();
    prepareFollowerFuture.join();
    // then
    verify(serverTransport, times(1)).unsubscribe(eq(1), eq(RequestType.QUERY));
    verify(serverTransport, times(1)).unsubscribe(eq(1), eq(RequestType.COMMAND));

    // it does not unsubscribe twice
    clearInvocations(serverTransport);
    final var transitionFollowerFuture =
        transitionStep.transitionTo(transitionContext, 2, nonLeaderRole);
    scheduler.workUntilDone();
    transitionFollowerFuture.join();
    verify(serverTransport, never()).unsubscribe(eq(1), eq(RequestType.QUERY));
    verify(serverTransport, never()).unsubscribe(eq(1), eq(RequestType.COMMAND));
  }

  @RegressionTest("https://github.com/camunda/camunda/issues/25897")
  public void shouldUnsubscribeTwiceWhenTransitioningFromFollowerToInactive() {
    // given
    when(transitionContext.getPartitionId()).thenReturn(1);
    final var transitionStep = new CommandApiServiceTransitionStep();
    final var prepareFollowerFuture =
        transitionStep.prepareTransition(transitionContext, 1, Role.FOLLOWER);
    scheduler.workUntilDone();
    prepareFollowerFuture.join();

    // when - transitions to FOLLOWER
    final var transitionFollowerFuture =
        transitionStep.transitionTo(transitionContext, 1, Role.FOLLOWER);
    scheduler.workUntilDone();
    transitionFollowerFuture.join();

    // then - subscriptions are cleaned up
    verify(serverTransport, times(1)).unsubscribe(eq(1), eq(RequestType.QUERY));
    verify(serverTransport, times(1)).unsubscribe(eq(1), eq(RequestType.COMMAND));

    clearInvocations(serverTransport);
    final var unregisterFuture =
        transitionStep.prepareTransition(transitionContext, 2, Role.INACTIVE);
    scheduler.workUntilDone();
    unregisterFuture.join();

    // when - transitions to INACTIVE
    final var transitionInactiveFuture =
        transitionStep.transitionTo(transitionContext, 2, Role.INACTIVE);
    scheduler.workUntilDone();
    transitionInactiveFuture.join();

    // then - subscriptions are cleaned up
    verify(serverTransport, times(1)).unsubscribe(eq(1), eq(RequestType.QUERY));
    verify(serverTransport, times(1)).unsubscribe(eq(1), eq(RequestType.COMMAND));
  }

  @RegressionTest("https://github.com/camunda/camunda/issues/25897")
  public void shouldUnsubscribeOnActorClosing() {
    // given
    when(logStream.newLogStreamWriter()).thenReturn(CompletableActorFuture.completed(mock()));
    when(transitionContext.getQueryService()).thenReturn(mock());

    final var registerFuture =
        commandApiService.registerHandlers(1, logStream, transitionContext.getQueryService());
    scheduler.workUntilDone();

    verify(serverTransport).subscribe(eq(1), eq(RequestType.QUERY), any());
    registerFuture.join();
    verify(serverTransport).subscribe(eq(1), eq(RequestType.COMMAND), any());

    // when - closing the actor
    final ActorFuture<Void> closeFuture = commandApiService.closeAsync();
    scheduler.workUntilDone();
    closeFuture.join(10, TimeUnit.SECONDS);

    // then - subscriptions are cleaned up
    verify(serverTransport).unsubscribe(eq(1), eq(RequestType.QUERY));
    verify(serverTransport).unsubscribe(eq(1), eq(RequestType.COMMAND));
  }
}
