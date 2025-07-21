/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import static io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.DeleteHistoryOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionDeleteExporterOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionDisableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionEnableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class ConfigurationChangeAppliersImplTest {

  @ParameterizedTest
  @MethodSource("provideArguments")
  void shouldReturnExpectedApplier(
      final ClusterConfigurationChangeOperation operation, final Class<?> expectedClass) {
    // given
    final var topologyChangeAppliers = new ConfigurationChangeAppliersImpl(null, null, null, null);

    // when
    final var applier = topologyChangeAppliers.getApplier(operation);

    // then
    assertThat(applier).isInstanceOf(expectedClass);
  }

  static Stream<Arguments> provideArguments() {

    final MemberId localMemberId = MemberId.from("1");
    return Stream.of(
        Arguments.of(new PartitionJoinOperation(localMemberId, 1, 1), PartitionJoinApplier.class),
        Arguments.of(new PartitionLeaveOperation(localMemberId, 1, 1), PartitionLeaveApplier.class),
        Arguments.of(new MemberJoinOperation(localMemberId), MemberJoinApplier.class),
        Arguments.of(new MemberLeaveOperation(localMemberId), MemberLeaveApplier.class),
        Arguments.of(
            new PartitionReconfigurePriorityOperation(localMemberId, 1, 1),
            PartitionReconfigurePriorityApplier.class),
        Arguments.of(
            new PartitionForceReconfigureOperation(localMemberId, 1, List.of()),
            PartitionForceReconfigureApplier.class),
        Arguments.of(
            new PartitionDisableExporterOperation(localMemberId, 1, "expId"),
            PartitionDisableExporterApplier.class),
        Arguments.of(
            new PartitionDeleteExporterOperation(localMemberId, 1, "expId"),
            PartitionDeleteExporterApplier.class),
        Arguments.of(
            new PartitionEnableExporterOperation(localMemberId, 1, "expId", Optional.empty()),
            PartitionEnableExporterApplier.class),
        Arguments.of(new DeleteHistoryOperation(localMemberId), DeleteHistoryApplier.class));
  }
}
