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
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ModeChangeRequestTransformerTest {

  private final MemberId id0 = MemberId.from("0");
  private final MemberId id1 = MemberId.from("1");

  @Test
  void shouldGenerateRecoveringOperationsForAllActiveMembers() {
    // given
    final var transformer = new ModeChangeRequestTransformer(Mode.RECOVERING);
    final var clusterConfiguration =
        ClusterConfiguration.init()
            .addMember(id0, MemberState.initializeAsActive(Map.of()))
            .addMember(id1, MemberState.initializeAsActive(Map.of()));

    // when
    final var result = transformer.operations(clusterConfiguration);

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get())
        .containsExactlyInAnyOrder(
            new ModeChangeOperation(id0, Mode.RECOVERING),
            new ModeChangeOperation(id1, Mode.RECOVERING));
  }

  @Test
  void shouldGenerateProcessingOperationsForAllRecoveringMembers() {
    // given
    final var transformer = new ModeChangeRequestTransformer(Mode.PROCESSING);
    final var clusterConfiguration =
        ClusterConfiguration.init()
            .addMember(id0, MemberState.initializeAsActive(Map.of()).toRecovering())
            .addMember(id1, MemberState.initializeAsActive(Map.of()).toRecovering());

    // when
    final var result = transformer.operations(clusterConfiguration);

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get())
        .containsExactlyInAnyOrder(
            new ModeChangeOperation(id0, Mode.PROCESSING),
            new ModeChangeOperation(id1, Mode.PROCESSING));
  }

  @Test
  void shouldOnlyTargetActiveMembersWhenEnteringRecovery() {
    // given — id0 active, id1 already recovering
    final var transformer = new ModeChangeRequestTransformer(Mode.RECOVERING);
    final var clusterConfiguration =
        ClusterConfiguration.init()
            .addMember(id0, MemberState.initializeAsActive(Map.of()))
            .addMember(id1, MemberState.initializeAsActive(Map.of()).toRecovering());

    // when
    final var result = transformer.operations(clusterConfiguration);

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get()).containsExactly(new ModeChangeOperation(id0, Mode.RECOVERING));
  }

  @Test
  void shouldOnlyTargetRecoveringMembersWhenExitingRecovery() {
    // given — id0 still active, id1 recovering
    final var transformer = new ModeChangeRequestTransformer(Mode.PROCESSING);
    final var clusterConfiguration =
        ClusterConfiguration.init()
            .addMember(id0, MemberState.initializeAsActive(Map.of()))
            .addMember(id1, MemberState.initializeAsActive(Map.of()).toRecovering());

    // when
    final var result = transformer.operations(clusterConfiguration);

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get()).containsExactly(new ModeChangeOperation(id1, Mode.PROCESSING));
  }

  @Test
  void shouldSucceedWithNoOperationsWhenEnteringRecoveryAndNoActiveMembers() {
    // given — all members already recovering, so the request is a no-op
    final var transformer = new ModeChangeRequestTransformer(Mode.RECOVERING);
    final var clusterConfiguration =
        ClusterConfiguration.init()
            .addMember(id0, MemberState.initializeAsActive(Map.of()).toRecovering());

    // when
    final var result = transformer.operations(clusterConfiguration);

    // then — idempotent: re-sending the request succeeds without generating operations
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get()).isEmpty();
  }

  @Test
  void shouldSucceedWithNoOperationsWhenExitingRecoveryAndNoRecoveringMembers() {
    // given — all members active, so the request is a no-op
    final var transformer = new ModeChangeRequestTransformer(Mode.PROCESSING);
    final var clusterConfiguration =
        ClusterConfiguration.init().addMember(id0, MemberState.initializeAsActive(Map.of()));

    // when
    final var result = transformer.operations(clusterConfiguration);

    // then — idempotent: re-sending the request succeeds without generating operations
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get()).isEmpty();
  }
}
