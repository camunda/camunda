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
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.UpdateRoutingState;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UpdateRoutingStateTransformerTest {

  private final ClusterConfiguration currentTopology =
      ClusterConfiguration.init()
          .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()));

  @Test
  void shouldGenerateUpdateRoutingStateOperationWhenEnabled() {
    // given
    final var routingState =
        Optional.of(
            new RoutingState(
                1L,
                new RoutingState.RequestHandling.AllPartitions(3),
                new RoutingState.MessageCorrelation.HashMod(3)));
    final var transformer = new UpdateRoutingStateTransformer(routingState);

    // when
    final var result = transformer.operations(currentTopology);

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get()).hasSize(1);
    assertThat(result.get().get(0)).isInstanceOf(UpdateRoutingState.class);
    final var operation = (UpdateRoutingState) result.get().get(0);
    assertThat(operation.routingState()).isEqualTo(routingState);
  }

  @Test
  void shouldGenerateUpdateRoutingStateOperationWithEmptyRoutingState() {
    // given
    final var transformer = new UpdateRoutingStateTransformer(Optional.empty());

    // when
    final var result = transformer.operations(currentTopology);

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get()).hasSize(1);
    assertThat(result.get().get(0)).isInstanceOf(UpdateRoutingState.class);
    final var operation = (UpdateRoutingState) result.get().get(0);
    assertThat(operation.routingState()).isEmpty();
  }
}
