/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.Scheme;
import io.camunda.zeebe.broker.system.configuration.partitioning.ZoneCfg;
import io.camunda.zeebe.broker.system.syspartition.SystemPartition;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Which region, i.e. zone, each broker running the system partition is in, and which regions host
 * data partitions. Data regions are ordered by descending zone priority, the order in which they
 * are preferred as failover targets. Empty for a cluster without zones.
 *
 * @param members the brokers running the system partition in each region, regions in configured
 *     order
 * @param dataRegions the regions hosting data partitions, by descending priority
 */
public record RegionLayout(Map<String, Set<String>> members, List<String> dataRegions) {
  public static final RegionLayout NONE = new RegionLayout(Map.of(), List.of());

  public RegionLayout {
    final var copy = new LinkedHashMap<String, Set<String>>();
    members.forEach((region, regionMembers) -> copy.put(region, Set.copyOf(regionMembers)));
    members = Collections.unmodifiableMap(copy);
    dataRegions = List.copyOf(dataRegions);
  }

  /** Returns the layout of the system partition's members as configured for this broker. */
  public static RegionLayout of(final BrokerCfg brokerCfg) {
    final var partitioning = brokerCfg.getExperimental().getPartitioning();
    if (partitioning.getScheme() != Scheme.ZONE_AWARE) {
      return NONE;
    }

    final var zones = partitioning.getZoneAware().zones();
    final var systemPartitionMembers = SystemPartition.members(brokerCfg);
    final var members = new LinkedHashMap<String, Set<String>>();
    for (final var zone : zones) {
      final var zoneMembers = new TreeSet<String>();
      systemPartitionMembers.stream()
          .filter(member -> member.isInZone(zone.name()))
          .map(MemberId::id)
          .forEach(zoneMembers::add);
      members.put(zone.name(), zoneMembers);
    }
    final var dataRegions =
        zones.stream()
            .filter(zone -> !zone.isControl())
            .sorted(Comparator.comparingInt(ZoneCfg::priority).reversed())
            .map(ZoneCfg::name)
            .toList();
    return new RegionLayout(members, dataRegions);
  }

  public boolean isEmpty() {
    return members.isEmpty();
  }

  public boolean isDataRegion(final String region) {
    return dataRegions.contains(region);
  }

  /**
   * Returns the state of every region with brokers running the system partition, in configured
   * order, given the state of each broker.
   */
  public Map<String, RegionState> regionStates(
      final Function<String, MemberLivenessState> stateOfMember) {
    final var states = new LinkedHashMap<String, RegionState>();
    members.forEach(
        (region, regionMembers) -> {
          if (!regionMembers.isEmpty()) {
            states.put(region, RegionState.of(regionMembers.stream().map(stateOfMember).toList()));
          }
        });
    return states;
  }
}
