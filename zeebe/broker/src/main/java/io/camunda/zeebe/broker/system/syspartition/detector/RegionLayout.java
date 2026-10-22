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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Which region, i.e. zone, each broker of the cluster is in, which of them run the system
 * partition, and which regions host data partitions. Data regions are ordered by descending zone
 * priority, the order in which they are preferred as failover targets. Empty for a cluster without
 * zones.
 *
 * @param members the brokers running the system partition in each region, regions in configured
 *     order
 * @param brokers every broker of each region, including those not running the system partition
 * @param dataRegions the regions hosting data partitions, by descending priority
 */
public record RegionLayout(
    Map<String, Set<String>> members, Map<String, Set<String>> brokers, List<String> dataRegions) {
  public static final RegionLayout NONE = new RegionLayout(Map.of(), Map.of(), List.of());

  public RegionLayout {
    members = copyOf(members);
    brokers = copyOf(brokers);
    dataRegions = List.copyOf(dataRegions);
  }

  private static Map<String, Set<String>> copyOf(final Map<String, Set<String>> regions) {
    final var copy = new LinkedHashMap<String, Set<String>>();
    regions.forEach((region, regionMembers) -> copy.put(region, Set.copyOf(regionMembers)));
    return Collections.unmodifiableMap(copy);
  }

  /** Returns the layout of the cluster's brokers as configured for this broker. */
  public static RegionLayout of(final BrokerCfg brokerCfg) {
    final var partitioning = brokerCfg.getExperimental().getPartitioning();
    if (partitioning.getScheme() != Scheme.ZONE_AWARE) {
      return NONE;
    }

    final var zones = partitioning.getZoneAware().zones();
    final var systemPartitionMembers = SystemPartition.members(brokerCfg);
    final var members = new LinkedHashMap<String, Set<String>>();
    final var brokers = new LinkedHashMap<String, Set<String>>();
    for (final var zone : zones) {
      final var zoneMembers = new TreeSet<String>();
      systemPartitionMembers.stream()
          .filter(member -> member.isInZone(zone.name()))
          .map(MemberId::id)
          .forEach(zoneMembers::add);
      members.put(zone.name(), zoneMembers);
      brokers.put(
          zone.name(),
          IntStream.range(0, zone.numberOfBrokers())
              .mapToObj(nodeId -> MemberId.from(zone.name(), nodeId).id())
              .collect(Collectors.toCollection(TreeSet::new)));
    }
    final var dataRegions =
        zones.stream()
            .filter(zone -> !zone.isControl())
            .sorted(Comparator.comparingInt(ZoneCfg::priority).reversed())
            .map(ZoneCfg::name)
            .toList();
    return new RegionLayout(members, brokers, dataRegions);
  }

  public boolean isEmpty() {
    return members.isEmpty();
  }

  public boolean isDataRegion(final String region) {
    return dataRegions.contains(region);
  }

  /** Returns every broker of every region. */
  public Set<String> allBrokers() {
    final var all = new LinkedHashSet<String>();
    brokers.values().forEach(all::addAll);
    return all;
  }

  /**
   * Returns the state of every region with brokers running the system partition, in configured
   * order, given the state of each such broker.
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
