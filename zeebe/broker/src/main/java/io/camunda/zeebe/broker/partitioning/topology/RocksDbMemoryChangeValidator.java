/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.system.configuration.RocksdbCfg;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeValidator;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.dynamic.config.util.PartitionCountProjection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.jspecify.annotations.NullMarked;

/**
 * Rejects a change that would give a broker more partitions than its RocksDB memory configuration
 * allows, at any point while the change and the other pending changes run, since that broker could
 * then no longer restart. Only brokers the change adds partitions to are checked, so a broker that
 * is already over its limit does not block unrelated changes.
 *
 * <p>Brokers can be configured differently, e.g. per region, so each broker advertises its own
 * limit through its member properties (see {@link #advertiseMaxPartitions(Member, RocksdbCfg)}). A
 * broker suspected to be down is checked against the limit it last advertised. A broker that hosts
 * partitions but is missing from the membership, because it was declared dead or has not been
 * discovered yet, has no known limit, so a change adding partitions to it is rejected until it is
 * back. Brokers that host no partitions yet, e.g. ones a scale-up is about to add, and brokers
 * running a version that does not advertise a limit, are not checked.
 */
@NullMarked
public final class RocksDbMemoryChangeValidator implements ClusterConfigurationChangeValidator {

  static final String MAX_PARTITIONS_PROPERTY = "rocksDbMaxPartitionsPerBroker";

  private final ClusterMembershipService membershipService;

  public RocksDbMemoryChangeValidator(final ClusterMembershipService membershipService) {
    this.membershipService = membershipService;
  }

  /**
   * Publishes how many partitions the local broker can start with to the other members. A broker
   * without a limit publishes the largest value instead of nothing, because members merge the
   * properties they receive and would keep a limit published before.
   */
  public static void advertiseMaxPartitions(final Member localMember, final RocksdbCfg rocksdbCfg) {
    final var maxPartitions = rocksdbCfg.getMaxPartitionsPerBroker().orElse(Integer.MAX_VALUE);
    localMember.properties().setProperty(MAX_PARTITIONS_PROPERTY, Integer.toString(maxPartitions));
  }

  @Override
  public Optional<String> validate(
      final CurrentClusterConfiguration configuration, final List<Phase> phases) {
    final var partitions = PartitionCountProjection.partitionsPerMember(configuration);
    final var pendingIncrease = PartitionCountProjection.pendingPeakIncrease(configuration);
    final var rejections =
        PartitionCountProjection.peakIncrease(configuration, phases).entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(MemberId::id)))
            .flatMap(
                e -> {
                  final var member = e.getKey();
                  final int current = partitions.getOrDefault(member, 0);
                  final int peak = current + pendingIncrease.getOrDefault(member, 0) + e.getValue();
                  return validatePartitionCount(member, current > 0, peak).stream();
                })
            .toList();

    return rejections.isEmpty() ? Optional.empty() : Optional.of(String.join(" ", rejections));
  }

  private Optional<String> validatePartitionCount(
      final MemberId memberId, final boolean hostsPartitions, final int partitionCount) {
    final var member = membershipService.getMember(memberId);
    if (member == null) {
      if (!hostsPartitions) {
        return Optional.empty();
      }
      return Optional.of(
          String.format(
              "Expected broker %s to be able to restart with %d partitions while this change is"
                  + " applied, but it is not a known member of the cluster, so its RocksDB memory"
                  + " limit is unknown. Retry once the broker is reachable.",
              memberId.id(), partitionCount));
    }
    final var maxPartitions = advertisedMaxPartitions(member);
    if (maxPartitions.isEmpty() || partitionCount <= maxPartitions.getAsInt()) {
      return Optional.empty();
    }
    return Optional.of(
        String.format(
            "Expected broker %s to be able to restart with %d partitions while this change is"
                + " applied, but its RocksDB memory configuration allows at most %d partitions."
                + " Increase the broker memory or the RocksDB memory configuration before applying"
                + " this change.",
            memberId.id(), partitionCount, maxPartitions.getAsInt()));
  }

  private static OptionalInt advertisedMaxPartitions(final Member member) {
    final var value = member.properties().getProperty(MAX_PARTITIONS_PROPERTY);
    if (value == null) {
      return OptionalInt.empty();
    }
    try {
      return OptionalInt.of(Integer.parseInt(value));
    } catch (final NumberFormatException e) {
      return OptionalInt.empty();
    }
  }
}
