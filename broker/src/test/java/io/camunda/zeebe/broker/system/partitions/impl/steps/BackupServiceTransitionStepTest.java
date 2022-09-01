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

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.backup.api.BackupManager;
import io.camunda.zeebe.backup.processing.CheckpointRecordsProcessor;
import io.camunda.zeebe.broker.system.partitions.TestPartitionTransitionContext;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
  BackupServiceTransitionStep step;

  @BeforeEach
  void setup() {
    transitionContext.setConcurrencyControl(TEST_CONCURRENCY_CONTROL);
    transitionContext.setActorSchedulingService(actorSchedulingService);
    lenient()
        .when(actorSchedulingService.submitActor(any()))
        .thenReturn(TEST_CONCURRENCY_CONTROL.completedFuture(null));

    lenient()
        .when(backupManagerPreviousRole.closeAsync())
        .thenReturn(TEST_CONCURRENCY_CONTROL.completedFuture(null));

    step = new BackupServiceTransitionStep();
  }

  @ParameterizedTest
  @MethodSource("provideTransitionThatShouldCloseService")
  void shouldCloseExistingService(final Role currentRole, final Role targetRole) {
    // given
    transitionContext.setCurrentRole(currentRole);
    if (currentRole != null && currentRole != Role.INACTIVE) {
      transitionContext.setBackupManager(backupManagerPreviousRole);
      transitionContext.setCheckpointProcessor(recordsProcessorPreviousRole);
    }

    // when
    step.prepareTransition(transitionContext, 1, targetRole).join();

    // then
    assertThat(transitionContext.getBackupManager()).isNull();
    verify(backupManagerPreviousRole).closeAsync();
    assertThat(transitionContext.getCheckpointProcessor()).isNull();
  }

  @ParameterizedTest
  @MethodSource("provideTransitionsThatShouldInstallService")
  void shouldReInstallService(final Role currentRole, final Role targetRole) {
    // given
    transitionContext.setCurrentRole(currentRole);
    if (currentRole != null && currentRole != Role.INACTIVE) {
      transitionContext.setBackupManager(backupManagerPreviousRole);
      transitionContext.setCheckpointProcessor(recordsProcessorPreviousRole);
    }

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
  @MethodSource("provideTransitionsThatShouldDoNothing")
  void shouldNotReInstallService(final Role currentRole, final Role targetRole) {
    // given
    transitionContext.setCurrentRole(currentRole);
    if (currentRole != null && currentRole != Role.INACTIVE) {
      transitionContext.setBackupManager(backupManagerPreviousRole);
      transitionContext.setCheckpointProcessor(recordsProcessorPreviousRole);
    }
    final var existingBackupManager = transitionContext.getBackupManager();
    final var existingRecordsProcessor = transitionContext.getCheckpointProcessor();

    // when
    transitionTo(targetRole);

    // then
    assertThat(transitionContext.getBackupManager()).isEqualTo(existingBackupManager);
    assertThat(transitionContext.getCheckpointProcessor()).isEqualTo(existingRecordsProcessor);
  }

  private static Stream<Arguments> provideTransitionThatShouldCloseService() {
    return Stream.of(
        Arguments.of(Role.FOLLOWER, Role.LEADER),
        Arguments.of(Role.CANDIDATE, Role.LEADER),
        Arguments.of(Role.LEADER, Role.FOLLOWER),
        Arguments.of(Role.LEADER, Role.INACTIVE),
        Arguments.of(Role.FOLLOWER, Role.INACTIVE),
        Arguments.of(Role.CANDIDATE, Role.INACTIVE));
  }

  private static Stream<Arguments> provideTransitionsThatShouldInstallService() {
    return Stream.of(
        Arguments.of(null, Role.FOLLOWER),
        Arguments.of(null, Role.LEADER),
        Arguments.of(null, Role.CANDIDATE),
        Arguments.of(Role.FOLLOWER, Role.LEADER),
        Arguments.of(Role.CANDIDATE, Role.LEADER),
        Arguments.of(Role.LEADER, Role.FOLLOWER),
        Arguments.of(Role.LEADER, Role.CANDIDATE),
        Arguments.of(Role.INACTIVE, Role.FOLLOWER),
        Arguments.of(Role.INACTIVE, Role.LEADER),
        Arguments.of(Role.INACTIVE, Role.CANDIDATE));
  }

  private static Stream<Arguments> provideTransitionsThatShouldDoNothing() {
    return Stream.of(
        Arguments.of(Role.CANDIDATE, Role.FOLLOWER),
        Arguments.of(Role.FOLLOWER, Role.CANDIDATE),
        Arguments.of(null, Role.INACTIVE));
  }

  private void transitionTo(final Role role) {
    step.prepareTransition(transitionContext, 1, role).join();
    step.transitionTo(transitionContext, 1, role).join();
    transitionContext.setCurrentRole(role);
  }
}
