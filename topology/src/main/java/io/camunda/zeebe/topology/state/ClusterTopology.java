/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.state;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.atomix.cluster.MemberId;
import java.io.IOException;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents the cluster topology which describes the current active, joining or leaving brokers
 * and the partitions that each broker replicates.
 *
 * <p>version - represents the current version of the topology. It is incremented when new
 * configuration change is triggered.
 *
 * <p>members - represents the state of each members
 *
 * <p>changes - keeps track of the ongoing configuration changes
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClusterTopology(
    long version, Map<MemberId, MemberState> members, ClusterChangePlan changes) {

  private static final int UNINITIALIZED_VERSION = -1;

  public static ClusterTopology uninitialized() {
    return new ClusterTopology(UNINITIALIZED_VERSION, Map.of(), ClusterChangePlan.empty());
  }

  public boolean isUninitialized() {
    return version == UNINITIALIZED_VERSION;
  }

  public static ClusterTopology init() {
    return new ClusterTopology(0, Map.of(), ClusterChangePlan.empty());
  }

  public static ClusterTopology decode(final byte[] serializedTopology) throws IOException {
    return new ObjectMapper().readValue(serializedTopology, ClusterTopology.class);
  }

  public byte[] encode() throws JsonProcessingException {
    return new ObjectMapper().writeValueAsBytes(this);
  }

  public ClusterTopology addMember(final MemberId memberId, final MemberState state) {
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

  /**
   * Returns a new ClusterTopology after merging this and other. This doesn't overwrite this or
   * other. If this.version == other.version then the new ClusterTopology contains merged members
   * and changes. Otherwise, it returns the one with highest version.
   *
   * @param other ClusterTopology to merge
   * @return merged ClusterTopology
   */
  public ClusterTopology merge(final ClusterTopology other) {
    if (version > other.version) {
      return this;
    } else if (other.version > version) {
      return other;
    } else {
      final var mergedMembers =
          Stream.concat(members.entrySet().stream(), other.members().entrySet().stream())
              .collect(
                  Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, MemberState::merge));

      // TODO: changes also have to be merged. We will do it when we add support for configuration
      // changes.
      return new ClusterTopology(version, ImmutableMap.copyOf(mergedMembers), changes);
    }
  }
}
