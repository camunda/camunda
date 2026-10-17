/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared {@link ZoneSpec} validation used by every zone-aware partition placement implementation —
 * currently {@link ZoneAwarePartitionDistributor} and {@link ZoneAwareAdditivePartitionReassigner}
 * — so the two can never drift apart on what a valid zone configuration or a valid target member
 * set looks like.
 */
final class ZoneSpecValidation {

  private ZoneSpecValidation() {}

  /**
   * @throws IllegalArgumentException if two zones share the same {@link ZoneSpec#name()}
   */
  static void validateZoneSpecs(final List<ZoneSpec> zoneSpecs) {
    zoneSpecs.stream()
        .collect(Collectors.groupingBy(ZoneSpec::name))
        .forEach(
            (name, zones) -> {
              if (zones.size() > 1) {
                throw new IllegalArgumentException(
                    "Expected zone names to be unique, but got " + zones);
              }
            });
  }

  /**
   * @throws IllegalStateException if the sum of every zone's {@link ZoneSpec#numberOfReplicas()}
   *     does not equal {@code replicationFactor}
   */
  static void validateReplicaSum(final List<ZoneSpec> zoneSpecs, final int replicationFactor) {
    final var totalReplicas = zoneSpecs.stream().mapToInt(ZoneSpec::numberOfReplicas).sum();
    if (totalReplicas != replicationFactor) {
      throw new IllegalStateException(
          "sum of numberOfReplicas across all zones (%d) does not match replicationFactor (%d)"
              .formatted(totalReplicas, replicationFactor));
    }
  }

  /**
   * @throws IllegalStateException if any zone has fewer members in {@code clusterMembers} than its
   *     {@link ZoneSpec#numberOfReplicas()}
   */
  static void validateZoneHasSufficientBrokers(
      final List<ZoneSpec> zoneSpecs, final Set<MemberId> clusterMembers) {
    for (final var spec : zoneSpecs) {
      final long zoneCount = clusterMembers.stream().filter(m -> m.isInZone(spec.name())).count();
      if (zoneCount < spec.numberOfReplicas()) {
        throw new IllegalStateException(
            "zone '%s' needs %d replicas but only has %d broker(s) in clusterMembers"
                .formatted(spec.name(), spec.numberOfReplicas(), zoneCount));
      }
    }
  }

  /**
   * @throws IllegalStateException if any member of {@code clusterMembers} has a non-null {@link
   *     MemberId#zone()} that is not one of {@code zoneSpecs}
   */
  static void validateKnownZonedMembers(
      final List<ZoneSpec> zoneSpecs, final Set<MemberId> clusterMembers) {
    final var zoneNames = zoneSpecs.stream().map(ZoneSpec::name).collect(Collectors.toSet());
    for (final var member : clusterMembers) {
      if (member.zone() != null && !zoneNames.contains(member.zone())) {
        throw new IllegalStateException(
            "member '%s' has zone '%s' which is not part of the configured zones"
                .formatted(member, member.zone()));
      }
    }
  }

  /**
   * @throws IllegalArgumentException if any member of {@code clusterMembers} is bare (has no zone),
   *     e.g. because a zone migration has not completed yet on all brokers
   */
  static void validateNoBareMembers(final Set<MemberId> clusterMembers) {
    final var bareMembers = clusterMembers.stream().filter(MemberId::isBare).toList();
    if (!bareMembers.isEmpty()) {
      throw new IllegalArgumentException(
          "Expected every member to belong to a zone, but found bare member(s) (likely a zone "
              + "migration still in progress): "
              + bareMembers);
    }
  }
}
