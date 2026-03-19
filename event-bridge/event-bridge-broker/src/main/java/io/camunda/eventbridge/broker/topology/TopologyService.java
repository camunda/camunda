/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.topology;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.utils.net.Address;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maintains an in-memory routing table of {@code partitionId → leader {@link Address}} by consuming
 * {@link ClusterMembershipEvent}s from SWIM.
 *
 * <p>Each broker writes its partition leadership into {@link Member#properties()} under keys of the
 * form {@code "eb.partition.<id>.leader"} with the value being the broker's own member ID. The
 * coordinator broker additionally sets {@code "eb.coordinator"} to its own member ID.
 *
 * <p>This class is safe for concurrent read access; mutations are driven by a single SWIM listener
 * thread.
 */
public final class TopologyService implements ClusterMembershipEventListener {

  /** Property key prefix for partition leadership: {@code "eb.partition.<id>.leader"}. */
  public static final String PARTITION_LEADER_KEY_PREFIX = "eb.partition.";

  public static final String PARTITION_LEADER_KEY_SUFFIX = ".leader";

  /** Property key that identifies the coordinator broker. */
  public static final String COORDINATOR_KEY = "eb.coordinator";

  private static final Logger LOG = LoggerFactory.getLogger(TopologyService.class);

  private final Map<Integer, Address> partitionLeaderMap = new ConcurrentHashMap<>();
  private final AtomicReference<Address> coordinatorAddress = new AtomicReference<>();
  private final String localMemberId;

  public TopologyService(final String localMemberId) {
    this.localMemberId = localMemberId;
  }

  /**
   * Seeds the routing table from the current membership snapshot (call once at startup after the
   * cluster is joined).
   */
  public void initialize(final ClusterMembershipService membershipService) {
    for (final Member member : membershipService.getMembers()) {
      applyMemberProperties(member);
    }
  }

  @Override
  public void event(final ClusterMembershipEvent event) {
    switch (event.type()) {
      case MEMBER_ADDED, METADATA_CHANGED -> applyMemberProperties(event.subject());
      case MEMBER_REMOVED -> removeMember(event.subject());
      default -> {
        // REACHABILITY_CHANGED — not relevant for routing
      }
    }
  }

  /** Returns the leader address for the given partition, or empty if unknown. */
  public Optional<Address> getLeaderAddress(final int partitionId) {
    return Optional.ofNullable(partitionLeaderMap.get(partitionId));
  }

  /** Returns the coordinator broker address, or empty if the coordinator is unknown or down. */
  public Optional<Address> getCoordinatorAddress() {
    return Optional.ofNullable(coordinatorAddress.get());
  }

  private void applyMemberProperties(final Member member) {
    final Address address = member.address();
    for (final var entry : member.properties().entrySet()) {
      final String key = String.valueOf(entry.getKey());
      if (key.startsWith(PARTITION_LEADER_KEY_PREFIX)
          && key.endsWith(PARTITION_LEADER_KEY_SUFFIX)) {
        final String middle =
            key.substring(
                PARTITION_LEADER_KEY_PREFIX.length(),
                key.length() - PARTITION_LEADER_KEY_SUFFIX.length());
        try {
          final int partitionId = Integer.parseInt(middle);
          partitionLeaderMap.put(partitionId, address);
          LOG.debug("Partition {} leader → {}", partitionId, address);
        } catch (final NumberFormatException e) {
          LOG.warn("Ignoring malformed partition leader property key: {}", key);
        }
      } else if (COORDINATOR_KEY.equals(key)) {
        coordinatorAddress.set(address);
        LOG.debug("Coordinator → {}", address);
      }
    }
  }

  private void removeMember(final Member member) {
    final Address address = member.address();
    partitionLeaderMap.values().removeIf(a -> a.equals(address));
    coordinatorAddress.compareAndSet(address, null);
    LOG.debug("Removed routing entries for departed member {}", address);
  }
}
