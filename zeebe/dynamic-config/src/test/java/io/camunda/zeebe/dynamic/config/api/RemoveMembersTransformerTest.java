/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RemoveMembersTransformerTest {

  final ClusterConfiguration currentTopology =
      ClusterConfiguration.init()
          .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()))
          .addMember(MemberId.from("2"), MemberState.initializeAsActive(Map.of()));

  @Test
  void shouldGenerateMemberJoinOperation() {
    // given
    final MemberId memberToRemove = MemberId.from("2");
    final var removeRequest = new RemoveMembersTransformer(Set.of(memberToRemove));

    // when
    final var result = removeRequest.operations(currentTopology);

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get()).containsExactly(new MemberLeaveOperation(memberToRemove));
  }

  @Test
  void shouldOnlyGenerateOperationsForExistingMembers() {
    // given
    final MemberId nonExistingMember = MemberId.from("3");
    final MemberId memberToRemove = MemberId.from("2");
    final var removeRequest =
        new RemoveMembersTransformer(Set.of(memberToRemove, nonExistingMember));

    // when
    final var result = removeRequest.operations(currentTopology);

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get()).containsExactly(new MemberLeaveOperation(memberToRemove));
  }

  @Test
  void shouldGenerateEmptyOperationsWhenNoMembersToBeRemoved() {
    final MemberId nonExistingMember = MemberId.from("3");
    final var removeRequest = new RemoveMembersTransformer(Set.of(nonExistingMember));

    // when
    final var result = removeRequest.operations(currentTopology);

    // then
    EitherAssert.assertThat(result).isRight();

    assertThat(result.get()).isEmpty();
  }
}
