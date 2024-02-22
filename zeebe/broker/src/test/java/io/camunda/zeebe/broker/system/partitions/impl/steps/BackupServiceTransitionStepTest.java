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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.backup.api.BackupManager;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.management.NoopBackupManager;
import io.camunda.zeebe.backup.processing.CheckpointRecordsProcessor;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ClusterCfg;
import io.camunda.zeebe.broker.system.partitions.TestPartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.impl.steps.PartitionTransitionTestArgumentProviders.TransitionsThatShouldCloseService;
import io.camunda.zeebe.broker.system.partitions.impl.steps.PartitionTransitionTestArgumentProviders.TransitionsThatShouldDoNothing;
import io.camunda.zeebe.broker.system.partitions.impl.steps.PartitionTransitionTestArgumentProviders.TransitionsThatShouldInstallService;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BackupServiceTransitionStepTest {

  private static final TestConcurrencyControl TEST_CONCURRENCY_CONTROL =
      new TestConcurrencyControl();
  TestPartitionTransitionContext transitionContext = new TestPartitionTransitionContext();

  @Mock BackupManager backupManagerPreviousRole;
  @Mock CheckpointRecordsProcessor recordsProcessorPreviousRole;
  @Mock ActorSchedulingService actorSchedulingService;
  @Mock BackupStore backupStore;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  BrokerCfg brokerCfg;

  @Mock ClusterCfg clusterCfg;

  @Mock RaftPartition raftPartition;
  BackupServiceTransitionStep step;

  @BeforeEach
  void setup() {
    transitionContext.setConcurrencyControl(TEST_CONCURRENCY_CONTROL);
    transitionContext.setActorSchedulingService(actorSchedulingService);
    transitionContext.setBackupStore(backupStore);
    transitionContext.setBrokerCfg(brokerCfg);
    transitionContext.setRaftPartition(raftPartition);

    lenient().when(brokerCfg.getCluster().getPartitionsCount()).thenReturn(3);
    lenient()
        .when(raftPartition.members())
        .thenReturn(Set.of(MemberId.from("1"), MemberId.from("2")));
    lenient().when(raftPartition.dataDirectory()).thenReturn(Path.of("/tmp/zeebe").toFile());

    lenient()
        .when(actorSchedulingService.submitActor(any()))
        .thenReturn(TEST_CONCURRENCY_CONTROL.completedFuture(null));

    lenient()
        .when(backupManagerPreviousRole.closeAsync())
        .thenReturn(TEST_CONCURRENCY_CONTROL.completedFuture(null));

    step = new BackupServiceTransitionStep();
  }

  @ParameterizedTest
  @ArgumentsSource(TransitionsThatShouldCloseService.class)
  void shouldCloseExistingService(final Role currentRole, final Role targetRole) {
    // given
    setUpCurrentRole(currentRole);

    // when
    step.prepareTransition(transitionContext, 1, targetRole).join();

    // then
    assertThat(transitionContext.getBackupManager()).isNull();
    verify(backupManagerPreviousRole).closeAsync();
    assertThat(transitionContext.getCheckpointProcessor()).isNull();
  }

  @ParameterizedTest
  @ArgumentsSource(TransitionsThatShouldInstallService.class)
  void shouldReInstallService(final Role currentRole, final Role targetRole) {
    // given
    setUpCurrentRole(currentRole);

    // when
    transitionTo(targetRole);

    // then
    assertThat(transitionContext.getBackupManager())
        .isNotNull()
        .isNotEqualTo(backupManagerPreviousRole);
    assertThat(transitionContext.getCheckpointProcessor())
        .isNotNull()
        .isNotEqualTo(recordsProcessorPreviousRole);
  }

  @ParameterizedTest
  @ArgumentsSource(TransitionsThatShouldDoNothing.class)
  void shouldNotReInstallService(final Role currentRole, final Role targetRole) {
    // given
    setUpCurrentRole(currentRole);
    final var existingBackupManager = transitionContext.getBackupManager();
    final var existingRecordsProcessor = transitionContext.getCheckpointProcessor();

    // when
    transitionTo(targetRole);

    // then
    assertThat(transitionContext.getBackupManager()).isEqualTo(existingBackupManager);
    assertThat(transitionContext.getCheckpointProcessor()).isEqualTo(existingRecordsProcessor);
  }

  @Test
  void shouldInstallNoopBackupManagerWhenFollower() {
    // given
    setUpCurrentRole(Role.LEADER);

    // when
    transitionTo(Role.FOLLOWER);

    // then
    assertThat(transitionContext.getBackupManager()).isInstanceOf(NoopBackupManager.class);
    assertThat(transitionContext.getCheckpointProcessor())
        .isNotNull()
        .isNotEqualTo(recordsProcessorPreviousRole);
  }

  @Test
  void shouldInstallNoopBackupManagerWhenNoBackupStore() {
    // given
    setUpCurrentRole(Role.FOLLOWER);
    transitionContext.setBackupStore(null);

    // when
    transitionTo(Role.LEADER);

    // then
    assertThat(transitionContext.getBackupManager()).isInstanceOf(NoopBackupManager.class);
    assertThat(transitionContext.getCheckpointProcessor())
        .isNotNull()
        .isNotEqualTo(recordsProcessorPreviousRole);
  }

  private void transitionTo(final Role role) {
    step.prepareTransition(transitionContext, 1, role).join();
    step.transitionTo(transitionContext, 1, role).join();
    transitionContext.setCurrentRole(role);
  }

  private void setUpCurrentRole(final Role currentRole) {
    transitionContext.setCurrentRole(currentRole);
    if (currentRole != null && currentRole != Role.INACTIVE) {
      transitionContext.setBackupManager(backupManagerPreviousRole);
      transitionContext.setCheckpointProcessor(recordsProcessorPreviousRole);
    }
  }
}
