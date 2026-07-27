/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.ClusterChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class PostScalingApplierTest {

  private final MemberId memberId = MemberId.from("1");
  private final Set<MemberId> clusterMembers = Set.of(memberId, MemberId.from("2"));
  private final ClusterChangeExecutor clusterChangeExecutor = mock(ClusterChangeExecutor.class);

  private static GlobalConfiguration globalConfigurationWith(
      final Map<MemberId, BrokerState> members) {
    return new GlobalConfiguration(
        GlobalConfiguration.INITIAL_VERSION,
        Optional.empty(),
        members,
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  private static CurrentClusterConfiguration currentClusterConfigurationWith(
      final GlobalConfiguration globalConfiguration) {
    return new CurrentClusterConfiguration(
        CurrentClusterConfiguration.INITIAL_VERSION,
        globalConfiguration,
        Map.of(),
        PhasedChangeState.empty());
  }

  @Test
  void shouldRejectIfMemberIsNotPartOfCluster() {
    // given
    final var initialConfig = globalConfigurationWith(Map.of());
    final var applier = new PostScalingApplier(memberId, clusterMembers, clusterChangeExecutor);

    // when
    final var result = applier.init(currentClusterConfigurationWith(initialConfig));

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("is not part of the current cluster configuration");
  }

  @Test
  void shouldExecutePostScaling() {
    // given
    final var initialConfig =
        globalConfigurationWith(Map.of(memberId, BrokerState.initializeAsActive()));
    final var applier = new PostScalingApplier(memberId, clusterMembers, clusterChangeExecutor);
    when(clusterChangeExecutor.postScaling(any()))
        .thenReturn(CompletableActorFuture.completed(null));

    // when
    final var initResult = applier.init(currentClusterConfigurationWith(initialConfig));
    assertThat(initResult).isRight();
    applier.apply().join();

    // then
    verify(clusterChangeExecutor, times(1)).postScaling(clusterMembers);
  }
}
