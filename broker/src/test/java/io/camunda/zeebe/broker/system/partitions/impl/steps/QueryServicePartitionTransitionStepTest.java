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

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.system.partitions.TestPartitionTransitionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.QueryService;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class QueryServicePartitionTransitionStepTest {

  TestPartitionTransitionContext transitionContext = new TestPartitionTransitionContext();

  private final ZeebeDb zeebeDb = mock(ZeebeDb.class);
  private final QueryService queryServiceFromPrevRole = mock(QueryService.class);

  private QueryServicePartitionTransitionStep step;

  @BeforeEach
  void setup() {

    transitionContext.setZeebeDb(zeebeDb);
    step = new QueryServicePartitionTransitionStep();
  }

  @ParameterizedTest
  @MethodSource("provideTransitionsThatShouldDoNothing")
  void shouldNotInstallQueryService(final Role currentRole, final Role targetRole) {
    // given
    initializeContext(currentRole);
    final var existingQueryService = transitionContext.getQueryService();

    // when
    transitionTo(targetRole);

    // then
    assertThat(transitionContext.getQueryService()).isEqualTo(existingQueryService);
  }

  @ParameterizedTest
  @MethodSource("provideTransitionsThatShouldCloseExistingQueryService")
  void shouldCloseExistingQueryService(final Role currentRole, final Role targetRole) {
    // given
    initializeContext(currentRole);

    // when
    step.prepareTransition(transitionContext, 1, targetRole);

    // then
    assertThat(transitionContext.getQueryService()).isNull();
  }

  @ParameterizedTest
  @MethodSource("provideTransitionsThatShouldInstallQueryService")
  void shouldInstallQueryService(final Role currentRole, final Role targetRole) {
    // given
    initializeContext(currentRole);
    final var existingQueryService = transitionContext.getQueryService();

    // when
    transitionTo(targetRole);

    // then
    assertThat(transitionContext.getQueryService()).isNotNull().isNotEqualTo(existingQueryService);
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
    assertThat(transitionContext.getQueryService()).isNull();
  }

  private static Stream<Arguments> provideTransitionsThatShouldDoNothing() {
    return Stream.of(
        Arguments.of(Role.CANDIDATE, Role.FOLLOWER),
        Arguments.of(Role.FOLLOWER, Role.CANDIDATE),
        Arguments.of(Role.CANDIDATE, Role.LEADER),
        Arguments.of(Role.FOLLOWER, Role.LEADER),
        Arguments.of(null, Role.INACTIVE));
  }

  private static Stream<Arguments> provideTransitionsThatShouldInstallQueryService() {
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

  private static Stream<Arguments> provideTransitionsThatShouldCloseExistingQueryService() {
    return Stream.of(
        Arguments.of(Role.LEADER, Role.FOLLOWER),
        Arguments.of(Role.LEADER, Role.CANDIDATE),
        Arguments.of(Role.LEADER, Role.INACTIVE),
        Arguments.of(Role.FOLLOWER, Role.INACTIVE),
        Arguments.of(Role.CANDIDATE, Role.INACTIVE));
  }

  private void initializeContext(final Role currentRole) {
    transitionContext.setCurrentRole(currentRole);
    if (currentRole != null && currentRole != Role.INACTIVE) {
      transitionContext.setQueryService(queryServiceFromPrevRole);
    }
  }

  private void transitionTo(final Role role) {
    step.prepareTransition(transitionContext, 1, role).join();
    step.transitionTo(transitionContext, 1, role).join();
    transitionContext.setCurrentRole(role);
  }
}
