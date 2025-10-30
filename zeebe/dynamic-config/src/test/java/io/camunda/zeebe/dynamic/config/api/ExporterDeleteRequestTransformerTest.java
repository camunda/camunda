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
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionDeleteExporterOperation;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExporterState.State;
import io.camunda.zeebe.dynamic.config.state.ExportersConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ExporterDeleteRequestTransformerTest {

  final String exporterId = "exporterA";
  private final MemberId id0 = MemberId.from("0");
  private final MemberId id1 = MemberId.from("1");
  private final DynamicPartitionConfig config =
      new DynamicPartitionConfig(
          new ExportersConfig(
              Map.of(exporterId, new ExporterState(1, State.ENABLED, Optional.empty()))));

  @Test
  void shouldGenerateOperationForAllPartitionsAndMembers() {
    // given
    final var transformer = new ExporterDeleteRequestTransformer(exporterId);

    final var clusterConfiguration =
        ClusterConfiguration.init()
            .addMember(id0, MemberState.initializeAsActive(Map.of()))
            .addMember(id1, MemberState.initializeAsActive(Map.of()))
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1, config)))
            .updateMember(id0, m -> m.addPartition(2, PartitionState.active(2, config)))
            .updateMember(id1, m -> m.addPartition(2, PartitionState.active(1, config)))
            .updateMember(id1, m -> m.addPartition(1, PartitionState.active(2, config)));

    // when
    final var result = transformer.operations(clusterConfiguration);

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get())
        .containsExactlyInAnyOrder(
            new PartitionDeleteExporterOperation(id0, 1, exporterId),
            new PartitionDeleteExporterOperation(id0, 2, exporterId),
            new PartitionDeleteExporterOperation(id1, 2, exporterId),
            new PartitionDeleteExporterOperation(id1, 1, exporterId));
  }
}
