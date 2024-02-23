/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageMonitor;
import io.camunda.zeebe.broker.system.partitions.TestPartitionTransitionContext;
import io.camunda.zeebe.broker.transport.backupapi.BackupApiRequestHandler;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.testing.TestActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class BackupApiRequestHandlerStepTest {

  @Mock AtomixServerTransport serverTransport;

  @Mock LogStream logStream;
  @Mock LogStreamWriter logStreamWriter;

  @Mock DiskSpaceUsageMonitor diskSpaceUsageMonitor;

  @Mock ActorSchedulingService actorSchedulingService;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  BrokerCfg brokerCfg;

  private final TestPartitionTransitionContext transitionContext =
      new TestPartitionTransitionContext();
  private BackupApiRequestHandlerStep step;

  @BeforeEach
  void setup() {
    transitionContext.setLogStream(logStream);
    transitionContext.setGatewayBrokerTransport(serverTransport);
    transitionContext.setConcurrencyControl(new TestConcurrencyControl());
    transitionContext.setDiskSpaceUsageMonitor(diskSpaceUsageMonitor);
    transitionContext.setActorSchedulingService(actorSchedulingService);
    transitionContext.setBrokerCfg(brokerCfg);

    step = new BackupApiRequestHandlerStep();
  }

  @Test
  void shouldInstallBackupRequestHandler() {
    // given
    when(logStream.newLogStreamWriter())
        .thenReturn(TestActorFuture.completedFuture(logStreamWriter));
    when(actorSchedulingService.submitActor(any()))
        .thenReturn(TestActorFuture.completedFuture(null));

    // when
    transitionTo(Role.LEADER);

    // then
    assertThat(transitionContext.getBackupApiRequestHandler()).isNotNull();
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"FOLLOWER", "CANDIDATE", "INACTIVE"})
  void shouldCloseBackupRequestHandler(
      final Role targetRole, @Mock final BackupApiRequestHandler requestHandlerFromPrevRole) {
    // given
    when(requestHandlerFromPrevRole.closeAsync()).thenReturn(TestActorFuture.completedFuture(null));
    // current role is leader
    transitionContext.setBackupApiRequestHandler(requestHandlerFromPrevRole);

    // when
    transitionTo(targetRole);

    // then
    assertThat(transitionContext.getBackupApiRequestHandler()).isNull();
    verify(requestHandlerFromPrevRole).closeAsync();
  }

  private void transitionTo(final Role role) {
    step.prepareTransition(transitionContext, 1, role).join();
    step.transitionTo(transitionContext, 1, role).join();
    transitionContext.setCurrentRole(role);
  }
}
