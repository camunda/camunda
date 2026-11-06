/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.atomix.raft.RaftServer.Role;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.broker.eventplane.EventPlaneRegistration;
import io.camunda.zeebe.broker.system.partitions.TestPartitionTransitionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

final class EventPlaneRegistrationStepTest {

  private final EventPlaneRegistration registration = mock(EventPlaneRegistration.class);
  private final EventPlaneRegistrationStep step = new EventPlaneRegistrationStep(registration);
  private final TestPartitionTransitionContext context =
      new TestPartitionTransitionContext() {
        @Override
        public PartitionId partitionId() {
          return new PartitionId("tenanta", 7);
        }
      };

  @Test
  void leadsInTheRegistrationAsLeader() {
    // when
    step.transitionTo(context, 1, Role.LEADER).join();

    // then
    verify(registration).lead(7);
  }

  @ParameterizedTest
  @EnumSource(value = Role.class, names = "LEADER", mode = Mode.EXCLUDE)
  void leadsOnlyAsLeader(final Role role) {
    // when
    step.transitionTo(context, 1, role).join();

    // then
    verify(registration, never()).lead(anyInt());
  }

  @ParameterizedTest
  @EnumSource(Role.class)
  void stopsLeadingOnTheNextTransition(final Role role) {
    // given
    step.transitionTo(context, 1, Role.LEADER).join();

    // when
    step.prepareTransition(context, 2, role).join();

    // then
    verify(registration).stopLeading(7);
  }

  @Test
  void stopsLeadingOnlyAfterLeading() {
    // when
    step.prepareTransition(context, 1, Role.FOLLOWER).join();

    // then
    verifyNoInteractions(registration);
  }

  @Test
  void doesNothingWithoutAnEventPlane() {
    // given
    final var withoutEventPlane = new EventPlaneRegistrationStep(null);

    // when
    withoutEventPlane.transitionTo(context, 1, Role.LEADER).join();
    withoutEventPlane.prepareTransition(context, 2, Role.FOLLOWER).join();

    // then
    verifyNoInteractions(registration);
  }
}
