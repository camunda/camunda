/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractAssert;

public final class ClusterTopologyAssert
    extends AbstractAssert<ClusterTopologyAssert, ClusterTopology> {

  private ClusterTopologyAssert(final ClusterTopology clusterTopology, final Class<?> selfType) {
    super(clusterTopology, selfType);
  }

  public static ClusterTopologyAssert assertThatClusterTopology(final ClusterTopology actual) {
    return new ClusterTopologyAssert(actual, ClusterTopologyAssert.class);
  }

  ClusterTopologyAssert isUninitialized() {
    assertThat(actual.isUninitialized()).isTrue();
    return this;
  }

  ClusterTopologyAssert isInitialized() {
    assertThat(actual.isUninitialized()).isFalse();
    return this;
  }

  public ClusterTopologyAssert hasMemberWithPartitions(
      final int member, final Collection<Integer> partitionIds) {
    final var memberId = MemberId.from(Integer.toString(member));
    assertThat(actual.members()).containsKey(memberId);
    assertThat(actual.members().get(memberId).partitions()).containsOnlyKeys(partitionIds);
    return this;
  }

  public MemberStateAssert member(final MemberId memberId) {
    assertThat(actual.members()).containsKey(memberId);
    return MemberStateAssert.assertThat(actual.members().get(memberId));
  }

  public MemberStateAssert member(final int memberId) {
    final MemberId id = MemberId.from(String.valueOf(memberId));
    assertThat(actual.members()).containsKey(id);
    return MemberStateAssert.assertThat(actual.members().get(id));
  }

  public ClusterTopologyAssert hasMemberWithState(final int member, final MemberState.State state) {
    final var memberId = MemberId.from(Integer.toString(member));
    assertThat(actual.members()).containsKey(memberId);
    assertThat(actual.members().get(memberId).state()).isEqualTo(state);
    return this;
  }

  public ClusterTopologyAssert hasOnlyMembers(final Set<Integer> members) {
    final var memberIds =
        members.stream().map(id -> MemberId.from(String.valueOf(id))).collect(Collectors.toSet());
    assertThat(actual.members()).containsOnlyKeys(memberIds);
    return this;
  }

  public ClusterTopologyAssert hasPendingOperationsWithSize(final int expectedSize) {
    if (expectedSize > 0) {
      assertThat(actual.hasPendingChanges()).isTrue();
      assertThat(actual.changes().ongoingChange().orElseThrow().pendingOperations())
          .hasSize(expectedSize);
    } else {
      assertThat(actual.hasPendingChanges()).isFalse();
    }
    return this;
  }

  public ClusterTopologyAssert doesNotHaveMember(final int id) {
    final MemberId memberId = MemberId.from(String.valueOf(id));
    assertThat(actual.members()).doesNotContainKey(memberId);
    return this;
  }

  public ClusterTopologyAssert hasVersion(final long version) {
    assertThat(actual.version()).isEqualTo(version);
    return this;
  }
}
