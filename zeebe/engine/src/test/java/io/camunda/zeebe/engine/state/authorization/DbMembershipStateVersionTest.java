/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.mutable.MutableMembershipState;
import io.camunda.zeebe.engine.state.mutable.MutablePolicyUpdateState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class DbMembershipStateVersionTest {

  private MutableProcessingState processingState;
  private MutableMembershipState membershipState;
  private MutablePolicyUpdateState policyUpdateState;

  @BeforeEach
  void setup() {
    membershipState = processingState.getMembershipState();
    policyUpdateState = processingState.getPolicyUpdateState();
  }

  @Test
  void shouldShowPendingMembershipOnlyToItsPolicyVersion() {
    // when
    membershipState.insertRelation(EntityType.USER, "alice", RelationType.ROLE, "r", 2);

    // then
    assertThat(membershipState.hasRelation(EntityType.USER, "alice", RelationType.ROLE, "r"))
        .isFalse();
    assertThat(membershipState.hasRelation(EntityType.USER, "alice", RelationType.ROLE, "r", 2))
        .isTrue();

    // when
    policyUpdateState.setCurrentVersion(2);

    // then
    assertThat(membershipState.hasRelation(EntityType.USER, "alice", RelationType.ROLE, "r"))
        .isTrue();
  }

  @Test
  void shouldHideMembershipRemovedAtCurrentVersion() {
    // given
    membershipState.insertRelation(EntityType.USER, "alice", RelationType.ROLE, "r", 0);

    // when
    membershipState.deleteRelation(EntityType.USER, "alice", RelationType.ROLE, "r", 1);

    // then: the removal is pending for a user command and visible to its own version
    assertThat(membershipState.hasRelation(EntityType.USER, "alice", RelationType.ROLE, "r"))
        .isTrue();
    assertThat(membershipState.hasRelation(EntityType.USER, "alice", RelationType.ROLE, "r", 1))
        .isFalse();

    // when
    policyUpdateState.setCurrentVersion(1);

    // then
    assertThat(membershipState.getMemberships(EntityType.USER, "alice", RelationType.ROLE))
        .isEmpty();
  }

  @Test
  void shouldVisitMembersVisibleToTheCommandVersion() {
    // given
    membershipState.insertRelation(EntityType.USER, "alice", RelationType.ROLE, "r", 0);
    membershipState.insertRelation(EntityType.GROUP, "g", RelationType.ROLE, "r", 0);
    membershipState.deleteRelation(EntityType.USER, "alice", RelationType.ROLE, "r", 1);
    membershipState.insertRelation(EntityType.CLIENT, "c", RelationType.ROLE, "r", 4);

    // when
    final var visibleAt0 = new ArrayList<String>();
    membershipState.forEachMember(
        RelationType.ROLE,
        "r",
        0,
        (type, id) -> {
          visibleAt0.add(type + ":" + id);
          return true;
        });
    final var visibleAt4 = new ArrayList<String>();
    membershipState.forEachMember(
        RelationType.ROLE,
        "r",
        4,
        (type, id) -> {
          visibleAt4.add(type + ":" + id);
          return true;
        });

    // then
    assertThat(visibleAt0).containsExactlyInAnyOrder("USER:alice", "GROUP:g");
    assertThat(visibleAt4).containsExactlyInAnyOrder("GROUP:g", "CLIENT:c");
  }
}
