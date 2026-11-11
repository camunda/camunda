/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.atomix.raft.zeebe.ZeebeLogAppender;
import io.camunda.zeebe.broker.logstreams.AtomixLogStorage;
import io.camunda.zeebe.broker.logstreams.rdbms.LogBlockRepository;
import io.camunda.zeebe.broker.logstreams.rdbms.LogBlockRepositoryException;
import io.camunda.zeebe.broker.logstreams.rdbms.RdbmsLogStorage;
import io.camunda.zeebe.broker.logstreams.rdbms.TermMismatchException;
import io.camunda.zeebe.broker.system.partitions.TestPartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.impl.steps.LogStoragePartitionTransitionStep.NotLeaderException;
import io.camunda.zeebe.broker.system.partitions.impl.steps.LogStoragePartitionTransitionStep.TransitionSupersededException;
import io.camunda.zeebe.broker.system.partitions.impl.steps.PartitionTransitionTestArgumentProviders.TransitionsThatShouldCloseService;
import io.camunda.zeebe.broker.system.partitions.impl.steps.PartitionTransitionTestArgumentProviders.TransitionsThatShouldDoNothing;
import io.camunda.zeebe.broker.system.partitions.impl.steps.PartitionTransitionTestArgumentProviders.TransitionsThatShouldInstallService;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.logstreams.storage.LogStorage.AppendListener;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import io.camunda.zeebe.util.health.HealthMonitor;
import java.util.Optional;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.EnumSource;

class LogStoragePartitionTransitionStepTest {

  TestPartitionTransitionContext transitionContext = new TestPartitionTransitionContext();

  private LogStoragePartitionTransitionStep step;
  private final RaftPartition raftPartition = mock(RaftPartition.class);
  private final RaftPartitionServer raftServer = mock(RaftPartitionServer.class);
  private final AtomixLogStorage logStorageFromPrevRole = mock(AtomixLogStorage.class);

  @BeforeEach
  void setup() {
    transitionContext.setLogStream(mock(LogStream.class));
    transitionContext.setComponentHealthMonitor(mock(HealthMonitor.class));

    when(raftPartition.getServer()).thenReturn(raftServer);
    when(raftServer.getTerm()).thenReturn(1L);
    when(raftServer.getAppender()).thenReturn(Optional.of(mock(ZeebeLogAppender.class)));
    transitionContext.setRaftPartition(raftPartition);

    step = new LogStoragePartitionTransitionStep();
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"INACTIVE", "FOLLOWER", "CANDIDATE"})
  void shouldThrowRecoverableExceptionOnTermMismatch(final Role currentRole) {
    // given
    initializeContext(currentRole);
    step.prepareTransition(transitionContext, 1, Role.LEADER);

    // simulate term change in Raft
    when(raftServer.getTerm()).thenReturn(2L);

    // when + then
    assertThatThrownBy(() -> step.transitionTo(transitionContext, 1, Role.LEADER).join())
        .hasCauseInstanceOf(NotLeaderException.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"INACTIVE", "FOLLOWER", "CANDIDATE"})
  void shouldThrowRecoverableExceptionWhenNoAppender(final Role currentRole) {
    // given
    initializeContext(currentRole);
    step.prepareTransition(transitionContext, 1, Role.LEADER);

    // simulate term change in Raft
    when(raftServer.getAppender()).thenReturn(Optional.empty());

    // when + then
    assertThatThrownBy(() -> step.transitionTo(transitionContext, 1, Role.LEADER).join())
        .hasCauseInstanceOf(NotLeaderException.class);
  }

  @ParameterizedTest
  @ArgumentsSource(TransitionsThatShouldCloseService.class)
  void shouldCloseExistingLogStorage(final Role currentRole, final Role targetRole) {
    // given
    initializeContext(currentRole);

    // when
    step.prepareTransition(transitionContext, 1, targetRole).join();

    // then
    assertThat(transitionContext.getLogStorage()).isNull();
  }

  @ParameterizedTest
  @ArgumentsSource(TransitionsThatShouldInstallService.class)
  void shouldInstallLogStorage(final Role currentRole, final Role targetRole) {
    // given
    initializeContext(currentRole);
    final var existingLogStorage = transitionContext.getLogStorage();

    // when
    transitionTo(targetRole);

    // then
    assertThat(transitionContext.getLogStorage()).isNotNull().isNotEqualTo(existingLogStorage);
  }

  @ParameterizedTest
  @ArgumentsSource(TransitionsThatShouldDoNothing.class)
  void shouldNotReInstallLogStorage(final Role currentRole, final Role targetRole) {
    // given
    initializeContext(currentRole);
    final var existingLogStorage = transitionContext.getLogStorage();

    // when
    transitionTo(targetRole);

    // then
    assertThat(transitionContext.getLogStorage()).isEqualTo(existingLogStorage);
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"FOLLOWER", "LEADER", "CANDIDATE"})
  void shouldCloseWhenTransitioningToInactive(final Role currentRole) {
    // given
    initializeContext(currentRole);

    // when
    transitionTo(Role.INACTIVE);

    // then
    assertThat(transitionContext.getLogStorage()).isNull();
  }

  private void initializeContext(final Role currentRole) {
    transitionContext.setCurrentRole(currentRole);
    if (currentRole != null && currentRole != Role.INACTIVE) {
      transitionContext.setLogStorage(logStorageFromPrevRole);
    }
  }

  private void transitionTo(final Role role) {
    step.prepareTransition(transitionContext, 1, role).join();
    step.transitionTo(transitionContext, 1, role).join();
    transitionContext.setCurrentRole(role);
  }

  @Nested
  class WithRdbmsLogStorage {

    private final LogBlockRepository repository = mock(LogBlockRepository.class);

    @BeforeEach
    void setup() {
      transitionContext.setLogBlockRepository(repository);
      when(repository.isReady()).thenReturn(true);
      when(repository.claimTerm(anyLong())).thenReturn(true);
      when(raftPartition.term()).thenReturn(1L);
    }

    @AfterEach
    void closeStorage() {
      if (transitionContext.getLogStorage() instanceof final RdbmsLogStorage storage) {
        storage.close();
      }
    }

    @Test
    void shouldClaimTermAndInstallWritableStorageForLeader() {
      // given
      initializeContext(Role.FOLLOWER);

      // when
      transitionTo(Role.LEADER);

      // then
      verify(repository).claimTerm(1);
      assertThat(transitionContext.getLogStorage()).isInstanceOf(RdbmsLogStorage.class);
      verify(raftServer, never()).addCommitListener(any());
    }

    @Test
    void shouldFailTransitionWhenHigherTermHoldsThePartition() {
      // given
      initializeContext(Role.FOLLOWER);
      when(repository.claimTerm(1)).thenReturn(false);
      step.prepareTransition(transitionContext, 1, Role.LEADER).join();

      // when + then
      assertThatThrownBy(() -> step.transitionTo(transitionContext, 1, Role.LEADER).join())
          .hasCauseInstanceOf(NotLeaderException.class);
    }

    @Test
    void shouldNotClaimTermWhenRaftTermChanged() {
      // given
      initializeContext(Role.FOLLOWER);
      when(raftServer.getTerm()).thenReturn(2L);
      step.prepareTransition(transitionContext, 1, Role.LEADER).join();

      // when + then
      assertThatThrownBy(() -> step.transitionTo(transitionContext, 1, Role.LEADER).join())
          .hasCauseInstanceOf(NotLeaderException.class);
      verify(repository, never()).claimTerm(anyLong());
    }

    @Test
    void shouldInstallReadOnlyStorageForFollower() {
      // given
      initializeContext(Role.LEADER);

      // when
      transitionTo(Role.FOLLOWER);

      // then
      final var storage = transitionContext.getLogStorage();
      assertThat(storage).isInstanceOf(RdbmsLogStorage.class);
      assertThatThrownBy(() -> append(storage)).isInstanceOf(UnsupportedOperationException.class);
      verify(repository, never()).claimTerm(anyLong());
    }

    @Test
    void shouldCloseStorageOfPreviousRole() {
      // given
      initializeContext(Role.FOLLOWER);
      transitionTo(Role.LEADER);
      final var leaderStorage = transitionContext.getLogStorage();

      // when
      transitionTo(Role.FOLLOWER);

      // then the closed storage drops appends instead of writing them
      append(leaderStorage);
      verify(repository, after(200).never()).append(anyLong(), any());
    }

    @Test
    void shouldStepDownWhenStorageFailsInItsTerm() {
      // given
      initializeContext(Role.FOLLOWER);
      transitionTo(Role.LEADER);
      doThrow(new TermMismatchException(1, 1)).when(repository).append(anyLong(), any());

      // when
      append(transitionContext.getLogStorage());

      // then
      verify(raftPartition, timeout(5_000)).stepDown();
    }

    @Test
    void shouldNotStepDownWhenStorageFailsAfterTermChanged() {
      // given
      initializeContext(Role.FOLLOWER);
      transitionTo(Role.LEADER);
      doThrow(new TermMismatchException(1, 1)).when(repository).append(anyLong(), any());
      when(raftPartition.term()).thenReturn(2L);

      // when
      append(transitionContext.getLogStorage());

      // then
      await()
          .until(
              () ->
                  mockingDetails(repository).getInvocations().stream()
                      .anyMatch(invocation -> invocation.getMethod().getName().equals("append")));
      verify(raftPartition, after(200).never()).stepDown();
    }

    @Test
    void shouldWaitForSchemaBeforeInstalling() {
      // given
      final var concurrencyControl = new TestConcurrencyControl(true);
      transitionContext.setConcurrencyControl(concurrencyControl);
      when(repository.isReady()).thenReturn(false);
      initializeContext(Role.FOLLOWER);
      step.prepareTransition(transitionContext, 1, Role.LEADER).join();

      // when
      final var installed = step.transitionTo(transitionContext, 1, Role.LEADER);
      concurrencyControl.runAll();

      // then
      assertThat(installed.isDone()).isFalse();
      verify(repository, never()).claimTerm(anyLong());

      // when
      when(repository.isReady()).thenReturn(true);
      concurrencyControl.runAll();

      // then
      assertThat(installed.isDone()).isTrue();
      installed.join();
      verify(repository).claimTerm(1);
      assertThat(transitionContext.getLogStorage()).isInstanceOf(RdbmsLogStorage.class);
    }

    @Test
    void shouldRetryClaimThatFailedOnTheDatabase() {
      // given
      final var concurrencyControl = new TestConcurrencyControl(true);
      transitionContext.setConcurrencyControl(concurrencyControl);
      when(repository.claimTerm(1))
          .thenThrow(new LogBlockRepositoryException("database unavailable"))
          .thenReturn(true);
      initializeContext(Role.FOLLOWER);
      step.prepareTransition(transitionContext, 1, Role.LEADER).join();

      // when
      final var installed = step.transitionTo(transitionContext, 1, Role.LEADER);

      // then
      assertThat(installed.isDone()).isFalse();

      // when
      concurrencyControl.runAll();

      // then
      installed.join();
      verify(repository, times(2)).claimTerm(1);
      assertThat(transitionContext.getLogStorage()).isInstanceOf(RdbmsLogStorage.class);
    }

    @Test
    void shouldStopWaitingWhenNewerTransitionIsAnnounced() {
      // given
      final var concurrencyControl = new TestConcurrencyControl(true);
      transitionContext.setConcurrencyControl(concurrencyControl);
      when(repository.isReady()).thenReturn(false);
      initializeContext(Role.FOLLOWER);
      step.prepareTransition(transitionContext, 1, Role.LEADER).join();
      final var installed = step.transitionTo(transitionContext, 1, Role.LEADER);

      // when
      step.onNewRaftRole(transitionContext, Role.FOLLOWER);
      concurrencyControl.runAll();

      // then
      assertThatThrownBy(installed::join).hasCauseInstanceOf(TransitionSupersededException.class);
      assertThat(transitionContext.getLogStorage()).isNull();
    }

    private void append(final LogStorage storage) {
      storage.append(
          1, 1, new DirectBufferWriter(new UnsafeBuffer(new byte[] {1})), new AppendListener() {});
    }
  }
}
