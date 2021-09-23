/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.system.partitions.StateController;
import io.camunda.zeebe.broker.system.partitions.TestPartitionTransitionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.util.sched.future.TestActorFuture;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class ZeebeDbPartitionTransitionStepTest {

  TestPartitionTransitionContext transitionContext = new TestPartitionTransitionContext();

  private final StateController stateController = mock(StateController.class);
  private final ZeebeDb zeebeDb = mock(ZeebeDb.class);
  private final ZeebeDb zeebeDbFromPrevRole = mock(ZeebeDb.class);

  private ZeebeDbPartitionTransitionStep step;

  @BeforeEach
  void setup() throws IOException {

    when(stateController.recover()).thenReturn(TestActorFuture.completedFuture(zeebeDb));
    transitionContext.setStateController(stateController);

    step = new ZeebeDbPartitionTransitionStep();
  }

  @ParameterizedTest
  @MethodSource("provideTransitionsThatShouldDoNothing")
  void shouldNotCloseZeebeDb(final Role currentRole, final Role targetRole) throws Exception {
    // given
    transitionContext.setCurrentRole(currentRole);
    if (currentRole != null && currentRole != Role.INACTIVE) {
      transitionContext.setZeebeDb(zeebeDbFromPrevRole);
    }
    final var existingZeebeDb = transitionContext.getZeebeDb();

    // when
    step.prepareTransition(transitionContext, 1, targetRole);

    // then
    assertThat(transitionContext.getZeebeDb()).isEqualTo(existingZeebeDb);
    verify(stateController, never()).closeDb();
  }

  @ParameterizedTest
  @MethodSource("provideTransitionsThatShouldDoNothing")
  void shouldNotReInstallZeebeDb(final Role currentRole, final Role targetRole) throws Exception {
    // given
    transitionContext.setCurrentRole(currentRole);
    if (currentRole != null && currentRole != Role.INACTIVE) {
      transitionContext.setZeebeDb(zeebeDbFromPrevRole);
    }
    final var existingZeebeDb = transitionContext.getZeebeDb();

    // when
    transitionTo(targetRole);

    // then
    assertThat(transitionContext.getZeebeDb()).isEqualTo(existingZeebeDb);
    verify(stateController, never()).closeDb();
  }

  @ParameterizedTest
  @MethodSource("provideTransitionsThatShouldCloseExistingZeebeDb")
  void shouldCloseExistingZeebeDb(final Role currentRole, final Role targetRole) throws Exception {
    // given
    transitionContext.setCurrentRole(currentRole);
    if (currentRole != null && currentRole != Role.INACTIVE) {
      transitionContext.setZeebeDb(zeebeDbFromPrevRole);
    }

    // when
    step.prepareTransition(transitionContext, 1, targetRole);

    // then
    assertThat(transitionContext.getZeebeDb()).isNull();
    verify(stateController).closeDb();
  }

  @ParameterizedTest
  @MethodSource("provideTransitionsThatShouldInstallZeebeDb")
  void shouldInstallZeebeDb(final Role currentRole, final Role targetRole) {
    // given
    transitionContext.setCurrentRole(currentRole);
    if (currentRole != null && currentRole != Role.INACTIVE) {
      transitionContext.setZeebeDb(zeebeDbFromPrevRole);
    }
    final var existingZeebeDb = transitionContext.getZeebeDb();

    // when
    transitionTo(targetRole);

    // then
    assertThat(transitionContext.getZeebeDb()).isNotEqualTo(existingZeebeDb);
    verify(stateController).recover();
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"FOLLOWER", "LEADER", "CANDIDATE"})
  void shouldCloseZeebeDBWhenTransitioningToInactive(final Role currentRole) throws Exception {
    // given
    transitionContext.setCurrentRole(currentRole);
    transitionContext.setZeebeDb(zeebeDbFromPrevRole);

    // when
    transitionTo(Role.INACTIVE);

    // then
    assertThat(transitionContext.getZeebeDb()).isNull();
    verify(stateController).closeDb();
    verify(stateController, never()).recover();
  }

  private static Stream<Arguments> provideTransitionsThatShouldDoNothing() {
    return Stream.of(
        Arguments.of(Role.CANDIDATE, Role.FOLLOWER),
        Arguments.of(Role.FOLLOWER, Role.CANDIDATE),
        Arguments.of(Role.CANDIDATE, Role.LEADER),
        Arguments.of(Role.FOLLOWER, Role.LEADER),
        Arguments.of(null, Role.INACTIVE));
  }

  private static Stream<Arguments> provideTransitionsThatShouldInstallZeebeDb() {
    return Stream.of(
        Arguments.of(null, Role.FOLLOWER),
        Arguments.of(null, Role.LEADER),
        Arguments.of(null, Role.CANDIDATE),
        Arguments.of(Role.LEADER, Role.FOLLOWER),
        Arguments.of(Role.LEADER, Role.CANDIDATE),
        Arguments.of(Role.INACTIVE, Role.FOLLOWER),
        Arguments.of(Role.INACTIVE, Role.LEADER),
        Arguments.of(Role.INACTIVE, Role.CANDIDATE));
  }

  private static Stream<Arguments> provideTransitionsThatShouldCloseExistingZeebeDb() {
    return Stream.of(
        Arguments.of(Role.LEADER, Role.FOLLOWER),
        Arguments.of(Role.LEADER, Role.CANDIDATE),
        Arguments.of(Role.LEADER, Role.INACTIVE),
        Arguments.of(Role.FOLLOWER, Role.INACTIVE),
        Arguments.of(Role.CANDIDATE, Role.INACTIVE));
  }

  private void transitionTo(final Role role) {
    step.prepareTransition(transitionContext, 1, role).join();
    step.transitionTo(transitionContext, 1, role).join();
    transitionContext.setCurrentRole(role);
  }
}
