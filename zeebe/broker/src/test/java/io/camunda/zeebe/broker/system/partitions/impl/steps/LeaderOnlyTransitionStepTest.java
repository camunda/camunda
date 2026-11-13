/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.broker.system.partitions.StateController;
import io.camunda.zeebe.broker.system.partitions.TestPartitionTransitionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.scheduler.testing.TestActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.util.health.HealthMonitor;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Uses the ZeebeDb step, the one that opens the partition state, to show that followers and
 * candidates keep no state while the leader does.
 */
final class LeaderOnlyTransitionStepTest {

  private final TestPartitionTransitionContext context = new TestPartitionTransitionContext();
  private final StateController stateController = mock(StateController.class);

  @SuppressWarnings("rawtypes")
  private final ZeebeDb zeebeDb = mock(ZeebeDb.class);

  private final AtomicLong openingTerm = new AtomicLong(-1);
  private final PartitionTransitionStep step =
      new LeaderOnlyTransitionStep(new ZeebeDbPartitionTransitionStep(openingTerm::set));

  @BeforeEach
  void setup() {
    when(stateController.recover()).thenReturn(TestActorFuture.completedFuture(zeebeDb));
    when(stateController.closeDb()).thenReturn(TestActorFuture.completedFuture(null));
    context.setStateController(stateController);
    context.setConcurrencyControl(new TestConcurrencyControl());
    context.setComponentHealthMonitor(mock(HealthMonitor.class));
    context.setCurrentRole(Role.INACTIVE);
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"FOLLOWER", "CANDIDATE"})
  void shouldNotOpenTheStateOnAFollowerOrCandidate(final Role role) {
    // when
    transitionTo(role, 1);

    // then
    assertThat(context.getZeebeDb()).isNull();
    verify(stateController, never()).recover();
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"FOLLOWER", "CANDIDATE"})
  void shouldCloseTheStateWhenTheLeaderStepsDown(final Role role) {
    // given
    transitionTo(Role.LEADER, 1);

    // when
    transitionTo(role, 2);

    // then
    assertThat(context.getZeebeDb()).isNull();
    verify(stateController).closeDb();
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"FOLLOWER", "CANDIDATE"})
  void shouldOpenTheStateWhenBecomingTheLeader(final Role role) {
    // given
    transitionTo(role, 1);

    // when
    transitionTo(Role.LEADER, 2);

    // then
    assertThat(context.getZeebeDb()).isEqualTo(zeebeDb);
    assertThat(openingTerm).hasValue(2);
  }

  @Test
  void shouldReopenTheStateForANewLeaderTerm() {
    // given
    transitionTo(Role.LEADER, 1);

    // when
    transitionTo(Role.LEADER, 2);

    // then
    verify(stateController).closeDb();
    assertThat(context.getZeebeDb()).isEqualTo(zeebeDb);
    assertThat(openingTerm).hasValue(2);
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"FOLLOWER", "CANDIDATE", "INACTIVE"})
  void shouldAnnounceOtherRolesAsInactive(final Role role) {
    // given
    final var wrapped = mock(PartitionTransitionStep.class);

    // when
    new LeaderOnlyTransitionStep(wrapped).onNewRaftRole(context, role);

    // then
    verify(wrapped).onNewRaftRole(context, Role.INACTIVE);
  }

  @Test
  void shouldAnnounceTheLeaderRole() {
    // given
    final var wrapped = mock(PartitionTransitionStep.class);

    // when
    new LeaderOnlyTransitionStep(wrapped).onNewRaftRole(context, Role.LEADER);

    // then
    verify(wrapped).onNewRaftRole(context, Role.LEADER);
  }

  @Test
  void shouldKeepTheNameOfTheStep() {
    assertThat(step.getName()).isEqualTo("ZeebeDb");
  }

  private void transitionTo(final Role role, final long term) {
    assertThat(step.prepareTransition(context, term, role)).succeedsWithin(Duration.ofMinutes(1));
    assertThat(step.transitionTo(context, term, role)).succeedsWithin(Duration.ofMinutes(1));
    context.setCurrentRole(role);
  }
}
