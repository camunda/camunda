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
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.record.value.EntityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class DbMembershipStateTest {
  private MutableProcessingState processingState;
  private MutableMembershipState membershipState;

  @BeforeEach
  public void setup() {
    membershipState = processingState.getMembershipState();
  }

  @Test
  void shouldAddUserToRole() {
    // when
    membershipState.insertRelation(EntityType.USER, "foo", RelationType.ROLE, "bar");

    // then
    assertThat(membershipState.getRelations(EntityType.USER, "foo", RelationType.ROLE))
        .singleElement()
        .isEqualTo("bar");
  }

  @Test
  void shouldAddManyRolesToUser() {
    // when
    membershipState.insertRelation(EntityType.USER, "m1", RelationType.ROLE, "r1");
    membershipState.insertRelation(EntityType.USER, "m1", RelationType.ROLE, "r2");
    membershipState.insertRelation(EntityType.USER, "m1", RelationType.ROLE, "r3");

    // then
    assertThat(membershipState.getRelations(EntityType.USER, "m1", RelationType.ROLE))
        .containsExactlyInAnyOrder("r1", "r2", "r3");
  }

  @Test
  void shouldNotFindRolesFromOtherUsers() {
    // when
    membershipState.insertRelation(EntityType.USER, "m1", RelationType.ROLE, "r1");
    membershipState.insertRelation(EntityType.USER, "m2", RelationType.ROLE, "r2");

    // then
    assertThat(membershipState.getRelations(EntityType.USER, "m1", RelationType.ROLE))
        .containsExactly("r1");
  }

  @Test
  void shouldRemoveUserFromRole() {
    // given
    membershipState.insertRelation(EntityType.USER, "foo", RelationType.ROLE, "bar");
    membershipState.insertRelation(EntityType.USER, "foo", RelationType.ROLE, "baz");

    // when
    membershipState.deleteRelation(EntityType.USER, "foo", RelationType.ROLE, "bar");

    // then
    assertThat(membershipState.getRelations(EntityType.USER, "foo", RelationType.ROLE))
        .containsExactly("baz");
  }
}
