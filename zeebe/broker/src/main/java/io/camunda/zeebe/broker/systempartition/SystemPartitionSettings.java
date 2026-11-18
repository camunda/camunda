/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.systempartition;

import io.atomix.cluster.MemberId;
import io.atomix.raft.partition.systempartition.SystemPartitionMembership;
import io.atomix.raft.partition.systempartition.VoterPolicy;
import io.atomix.raft.partition.systempartition.VoterPolicy.ZoneVoters;
import io.camunda.zeebe.broker.partitioning.topology.StaticConfigurationGenerator;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.Scheme;
import io.camunda.zeebe.broker.system.configuration.partitioning.ZoneCfg;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * What the system partition takes from the static configuration (design 5.1, 5.4.1): the brokers of
 * the static configuration and the voter policy.
 *
 * <p>Stub of the policy record (D18): the system partition has no state machine yet, thus the
 * static policy is the only policy. A change of the policy at runtime is not possible.
 *
 * @param staticMembers the brokers of the static configuration
 * @param staticPolicy the voter policy: with zones, the {@code numberOfReplicas} of each zone, in
 *     the order of the zone priorities; without zones, the largest odd number that is not more than
 *     the replication factor and the cluster size
 */
public record SystemPartitionSettings(Set<MemberId> staticMembers, VoterPolicy staticPolicy) {

  public static SystemPartitionSettings of(final BrokerCfg brokerCfg) {
    final var partitioning = brokerCfg.getExperimental().getPartitioning();
    final var members =
        StaticConfigurationGenerator.getRaftGroupMembers(brokerCfg.getCluster(), partitioning);
    final VoterPolicy policy;
    if (partitioning.getScheme() == Scheme.ZONE_AWARE) {
      policy =
          new VoterPolicy(
              partitioning.getZoneAware().zones().stream()
                  .sorted(Comparator.comparingInt(ZoneCfg::priority).reversed())
                  .map(zone -> new ZoneVoters(zone.name(), zone.numberOfReplicas()))
                  .toList());
    } else {
      int voters =
          Math.min(
              brokerCfg.getCluster().getReplicationFactor(),
              brokerCfg.getCluster().getClusterSize());
      if (voters % 2 == 0) {
        voters--;
      }
      policy = new VoterPolicy(List.of(new ZoneVoters("", Math.max(1, voters))));
    }
    return new SystemPartitionSettings(Set.copyOf(members), policy);
  }

  /** The hash of the policy and the initial configuration, for the probe (5.4.2). */
  public String initialConfigurationHash() {
    return SystemPartitionMembership.initialConfigurationHash(staticPolicy, staticMembers);
  }
}
