/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.MemberJoinOperation;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AddMembersTransformerTest {

  final ClusterTopology currentTopology =
      ClusterTopology.init()
          .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()));

  @Test
  void shouldGenerateMemberJoinOperation() {
    // given
    final MemberId newMember = MemberId.from("2");
    final var addRequest = new AddMembersTransformer(Set.of(newMember));

    // when
    final var result = addRequest.operations(currentTopology);

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get()).containsExactly(new MemberJoinOperation(newMember));
  }

  @Test
  void shouldOnlyGenerateOperationsForNewMembers() {
    // given
    final MemberId newMember = MemberId.from("2");
    final MemberId existingMember = MemberId.from("1");
    final var addRequest = new AddMembersTransformer(Set.of(existingMember, newMember));

    // when
    final var result = addRequest.operations(currentTopology);

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get()).hasSize(1).containsExactly(new MemberJoinOperation(newMember));
  }

  @Test
  void shouldGenerateEmptyOperationsWhenNoNewMembers() {
    final MemberId existingMember = MemberId.from("1");
    final var addRequest = new AddMembersTransformer(Set.of(existingMember));

    // when
    final var result = addRequest.operations(currentTopology);

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get()).isEmpty();
  }
}
