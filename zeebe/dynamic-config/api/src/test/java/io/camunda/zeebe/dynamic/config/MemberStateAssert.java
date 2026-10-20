/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import java.util.Map;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;

public final class MemberStateAssert extends AbstractAssert<MemberStateAssert, MemberState> {

  private MemberStateAssert(final MemberState memberState, final Class<?> selfType) {
    super(memberState, selfType);
  }

  public static MemberStateAssert assertThat(final MemberState actual) {
    return new MemberStateAssert(actual, MemberStateAssert.class);
  }

  public MemberStateAssert hasPartitionWithState(
      final int partitionId, final PartitionState.State state) {
    hasPartitionSatisfying(
        partitionId,
        partitionState -> Assertions.assertThat(partitionState.state()).isEqualTo(state));
    return this;
  }

  public MemberStateAssert doesNotContainPartition(final int partitionId) {
    final Map<Integer, PartitionState> partitions = actual.partitions();
    Assertions.assertThat(partitions).doesNotContainKey(partitionId);
    return this;
  }

  public MemberStateAssert hasPartitionWithPriority(final int partitionId, final int priority) {
    hasPartitionSatisfying(
        partitionId,
        partitionState -> Assertions.assertThat(partitionState.priority()).isEqualTo(priority));
    return this;
  }

  public MemberStateAssert hasPartitionSatisfying(
      final int partitionId, final Consumer<PartitionState> partitionStateCondition) {
    final Map<Integer, PartitionState> partitions = actual.partitions();
    Assertions.assertThat(partitions).hasEntrySatisfying(partitionId, partitionStateCondition);
    return this;
  }
}
