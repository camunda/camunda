/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.UpdatePartitionDistributorConfigOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.RoundRobinConfig;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class UpdatePartitionDistributorConfigApplierTest {

  private final GlobalConfiguration initialConfig =
      new GlobalConfiguration(
          GlobalConfiguration.INITIAL_VERSION,
          Optional.empty(),
          Map.of(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty());
  private final CurrentClusterConfiguration currentClusterConfiguration =
      new CurrentClusterConfiguration(
          CurrentClusterConfiguration.INITIAL_VERSION,
          initialConfig,
          Map.of(),
          PhasedChangeState.empty());

  @Test
  void shouldUpdatePartitionDistributorConfig() {
    // given
    final var newConfig = new RoundRobinConfig();
    final var operation =
        new UpdatePartitionDistributorConfigOperation(MemberId.from("1"), newConfig);
    final var applier = new UpdatePartitionDistributorConfigApplier(operation);

    // when
    final var initResult = applier.init(currentClusterConfiguration);
    assertThat(initResult).isRight();
    final var updatedConfig = applier.apply().join().apply(initialConfig);

    // then
    Assertions.assertThat(updatedConfig.partitionDistributorConfig()).contains(newConfig);
  }
}
