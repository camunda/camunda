/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractAssert;

public final class ClusterConfigurationAssert
    extends AbstractAssert<ClusterConfigurationAssert, ClusterConfiguration> {

  private ClusterConfigurationAssert(
      final ClusterConfiguration clusterConfiguration, final Class<?> selfType) {
    super(clusterConfiguration, selfType);
  }

  public static ClusterConfigurationAssert assertThatClusterTopology(
      final ClusterConfiguration actual) {
    return new ClusterConfigurationAssert(actual, ClusterConfigurationAssert.class);
  }

  ClusterConfigurationAssert isUninitialized() {
    assertThat(actual.isUninitialized()).isTrue();
    return this;
  }

  ClusterConfigurationAssert isInitialized() {
    assertThat(actual.isUninitialized()).isFalse();
    return this;
  }

  public ClusterConfigurationAssert hasMemberWithPartitions(
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

  public ClusterConfigurationAssert hasMemberWithState(
      final int member, final MemberState.State state) {
    final var memberId = MemberId.from(Integer.toString(member));
    assertThat(actual.members()).containsKey(memberId);
    assertThat(actual.members().get(memberId).state()).isEqualTo(state);
    return this;
  }

  public ClusterConfigurationAssert hasOnlyMembers(final Set<Integer> members) {
    final var memberIds =
        members.stream().map(id -> MemberId.from(String.valueOf(id))).collect(Collectors.toSet());
    assertThat(actual.members()).containsOnlyKeys(memberIds);
    return this;
  }

  public ClusterConfigurationAssert hasPendingOperationsWithSize(final int expectedSize) {
    if (expectedSize > 0) {
      assertThat(actual.hasPendingChanges()).isTrue();
      assertThat(actual.pendingChanges().orElseThrow().pendingOperations()).hasSize(expectedSize);
    } else {
      assertThat(actual.hasPendingChanges()).isFalse();
    }
    return this;
  }

  public ClusterConfigurationAssert doesNotHaveMember(final int id) {
    final MemberId memberId = MemberId.from(String.valueOf(id));
    assertThat(actual.members()).doesNotContainKey(memberId);
    return this;
  }

  public ClusterConfigurationAssert hasVersion(final long version) {
    assertThat(actual.version()).isEqualTo(version);
    return this;
  }

  public ClusterConfigurationAssert hasRoutingState() {
    assertThat(actual.routingState()).isPresent();
    return this;
  }

  public ClusterConfigurationAssert hasNoRoutingState() {
    assertThat(actual.routingState()).isEmpty();
    return this;
  }

  public RoutingStateAssert routingState() {
    assertThat(actual.routingState()).isPresent();
    return RoutingStateAssert.assertThat(actual.routingState().orElseThrow());
  }

  /**
   * Asserts that the actual topology has the same topology as the expected topology ignoring
   * timestamps and versions.
   */
  public ClusterConfigurationAssert hasSameTopologyAs(final ClusterConfiguration expected) {
    assertThat(actual.members().keySet())
        .containsExactlyInAnyOrderElementsOf(expected.members().keySet());

    // Compare MemberStates without timestamp and version
    assertThat(actual.members())
        .usingRecursiveComparison()
        .ignoringFieldsMatchingRegexes(".*lastUpdated", ".*version")
        .isEqualTo(expected.members());

    // compare last change without timestamps
    final var optionalActualCompletedChange = actual.lastChange();
    final var optionalExpectedCompletedChange = expected.lastChange();
    assertThat(optionalActualCompletedChange)
        .usingRecursiveComparison()
        .ignoringFieldsMatchingRegexes(".*startedAt", ".*completedAt")
        .isEqualTo(optionalExpectedCompletedChange);

    // compare ongoing change without timestamps
    final var optionalActualOngoingChange = actual.pendingChanges();
    final var optionalExpectedOngoingChange = expected.pendingChanges();
    assertThat(optionalActualOngoingChange)
        .usingRecursiveComparison()
        .ignoringFieldsMatchingRegexes(".*startedAt", ".*version")
        .isEqualTo(optionalExpectedOngoingChange);

    // compare routing state without version
    assertThat(actual.routingState())
        .usingRecursiveComparison()
        .ignoringFieldsOfTypesMatchingRegexes(".*version")
        .isEqualTo(expected.routingState());

    return this;
  }
}
