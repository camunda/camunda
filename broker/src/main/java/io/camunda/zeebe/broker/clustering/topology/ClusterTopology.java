/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.clustering.topology;

import com.google.common.collect.ImmutableMap;
import io.atomix.cluster.MemberId;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents the cluster topology which describes the current active, joining or leaving brokers
 * and the partitions that each broker replicates.
 */
public record ClusterTopology(
    long version, Map<MemberId, MemberState> members, ClusterChangePlan changes) {

  static ClusterTopology init() {
    return new ClusterTopology(0, Map.of(), ClusterChangePlan.empty());
  }

  ClusterTopology addMember(final MemberId memberId, final MemberState state) {
    if (members.containsKey(memberId)) {
      throw new IllegalStateException(
          String.format(
              "Expected add a new member, but member %s already exists with state %s",
              memberId.id(), members.get(memberId)));
    }

    final var newMembers =
        ImmutableMap.<MemberId, MemberState>builder().putAll(members).put(memberId, state).build();
    return new ClusterTopology(version, newMembers, changes);
  }

  ClusterTopology updateMember(
      final MemberId memberId, final UnaryOperator<MemberState> memberStateUpdater) {
    if (!members.containsKey(memberId)) {
      throw new IllegalStateException(
          String.format("Expected to update member %s, but member does not exist", memberId.id()));
    }
    final var updateMemberState = memberStateUpdater.apply(members.get(memberId));
    final var newMembers =
        ImmutableMap.<MemberId, MemberState>builder()
            .putAll(members)
            .put(memberId, updateMemberState)
            .buildKeepingLast();
    return new ClusterTopology(version, newMembers, changes);
  }

  ClusterTopology merge(final ClusterTopology other) {
    final var newVersion = Math.max(version, other.version);
    final var mergedMembers =
        Stream.concat(members.entrySet().stream(), other.members().entrySet().stream())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, MemberState::merge));

    // TODO: changes also have to be merged. We will do it when we add support for configuration
    // changes.
    return new ClusterTopology(newVersion, ImmutableMap.copyOf(mergedMembers), changes);
  }
}
