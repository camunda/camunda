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
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.dynamic.config.util.MemberIdArbitraries;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.domains.Domain;
import org.junit.jupiter.api.Test;

class AddMembersTransformerTest {

  final ClusterConfiguration currentTopology =
      ClusterConfiguration.init()
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

  @Property(tries = 100)
  @Domain(MemberIdArbitraries.class)
  void shouldOnlyGenerateOperationsForNewMembersProperty(
      @ForAll final Set<MemberId> candidateMembers) {
    // given
    final var addRequest = new AddMembersTransformer(candidateMembers);

    // when
    final var result = addRequest.operations(currentTopology);

    // then
    EitherAssert.assertThat(result)
        .isRight()
        .right()
        .isEqualTo(
            result.get().stream()
                .sorted(Comparator.comparing(ClusterConfigurationChangeOperation::memberId))
                .toList());
  }

  @Test
  void shouldReturnErrorForBareIdInFullyZonedCluster() {
    final MemberId existingMember = MemberId.from("1");
    final var addRequest = new AddMembersTransformer(Set.of(existingMember));
    final var zonedConfiguration =
        ClusterConfiguration.builder()
            .members(Map.of(MemberId.from("zone-a", 0), MemberState.uninitialized()))
            .partitionDistributorConfig(
                Optional.of(new ZoneAwareConfig(List.of(new ZoneSpec("zone-a", 1, 1)))))
            .build();

    // when
    final var result = addRequest.operations(zonedConfiguration);

    // then
    EitherAssert.assertThat(result)
        .isLeft()
        .left()
        .satisfies(e -> assertThat(e).isInstanceOf(InvalidRequest.class));
  }
}
