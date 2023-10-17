/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.changes;

import static io.camunda.zeebe.topology.state.TopologyChangeOperation.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class TopologyChangeAppliersImplTest {

  @ParameterizedTest
  @MethodSource("provideArguments")
  void shouldReturnExpectedApplier(
      final TopologyChangeOperation operation, final Class<?> expectedClass) {
    // given
    final var topologyChangeAppliers = new TopologyChangeAppliersImpl(null, null);

    // when
    final var applier = topologyChangeAppliers.getApplier(operation);

    // then
    assertThat(applier).isInstanceOf(expectedClass);
  }

  static Stream<Arguments> provideArguments() {

    final MemberId localMemberId = MemberId.from("1");
    return Stream.of(
        Arguments.of(new PartitionJoinOperation(localMemberId, 1, 1), PartitionJoinApplier.class),
        Arguments.of(new PartitionLeaveOperation(localMemberId, 1), PartitionLeaveApplier.class),
        Arguments.of(new MemberJoinOperation(localMemberId), MemberJoinApplier.class),
        Arguments.of(new MemberLeaveOperation(localMemberId), MemberLeaveApplier.class),
        Arguments.of(
            new PartitionReconfigurePriorityOperation(localMemberId, 1, 1),
            PartitionReconfigurePriorityApplier.class));
  }
}
