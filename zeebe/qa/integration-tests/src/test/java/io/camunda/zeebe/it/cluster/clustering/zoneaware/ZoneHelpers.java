/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering.zoneaware;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.configuration.Partitioning.Scheme;
import io.camunda.configuration.Zone;
import io.camunda.configuration.ZoneAware;
import io.camunda.zeebe.management.cluster.BrokerId;
import io.camunda.zeebe.management.cluster.PartitionState;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.actuator.RebalanceActuator;
import io.camunda.zeebe.qa.util.cluster.TestApplication;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;

public class ZoneHelpers {

  /**
   * Generous upper bound for the per-physical-tenant assertions below. A non-default tenant's Raft
   * groups are not covered by the {@code @TestZeebe} extension's readiness wait, which only looks
   * at the gateway's unscoped topology, so they may still be electing when a test body starts.
   */
  private static final Duration ASSERTION_TIMEOUT = Duration.ofSeconds(60);

  public static TestCluster createCluster(
      final String name,
      final List<Zone> zones,
      final int partitionCount,
      final int replicationFactor) {
    return TestCluster.builder()
        .withName(name)
        .withEmbeddedGateway(true)
        .withBrokersCount(zones.stream().mapToInt(Zone::numberOfBrokers).sum())
        .withPartitionsCount(partitionCount)
        .withReplicationFactor(replicationFactor)
        .multiZone(zones)
        .build()
        .start();
  }

  /** Starts a broker with id in a zone without adding it to the topology. */
  public static TestStandaloneBroker startBrokerInZone(
      final TestCluster cluster,
      final String zone,
      final int nodeId,
      final int clusterSize,
      final List<Zone> newZones) {
    return startBrokerInZone(cluster, zone, nodeId, clusterSize, newZones, ignored -> {});
  }

  /**
   * Same as {@link #startBrokerInZone(TestCluster, String, int, int, List)}, but applies {@code
   * additionalConfig} before starting - e.g. to layer physical-tenant configuration ({@code
   * PhysicalTenantsITHelper#configure}) onto a replacement broker joining a cluster that already
   * runs non-default tenants, which this method knows nothing about on its own.
   */
  public static TestStandaloneBroker startBrokerInZone(
      final TestCluster cluster,
      final String zone,
      final int nodeId,
      final int clusterSize,
      final List<Zone> newZones,
      final Consumer<TestStandaloneBroker> additionalConfig) {
    // a stopped broker (e.g. one belonging to a zone this test just killed) cannot bootstrap a
    // joiner that only ever learns of this single seed
    final var contactPoint =
        cluster.brokers().values().stream()
            .filter(TestApplication::isStarted)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("no started broker to contact"))
            .address(TestZeebePort.CLUSTER);
    final var broker =
        new TestStandaloneBroker()
            .withUnauthenticatedAccess()
            .withUnifiedConfig(
                cfg -> {
                  final var clusterCfg = cfg.getCluster();
                  clusterCfg.setName(cluster.name());
                  clusterCfg.setInitialContactPoints(List.of(contactPoint));
                  clusterCfg.setZone(zone);
                  clusterCfg.setSize(clusterSize);
                  clusterCfg.setNodeId(nodeId);
                  clusterCfg.getPartitioning().setScheme(Scheme.ZONE_AWARE);
                  clusterCfg.getPartitioning().setZoneAware(new ZoneAware(newZones));
                  cfg.getData().getSecondaryStorage().setAutoconfigureCamundaExporter(false);
                });
    additionalConfig.accept(broker);

    broker.start();

    return broker;
  }

  public static void assertZoneHostsPartitions(
      final ClusterActuator actuator, final String zone, final int nodeId) {
    final var brokerId = new BrokerId.String(MemberId.from(zone, nodeId).toString());
    Awaitility.await()
        .untilAsserted(
            () -> {
              final var broker =
                  actuator.getTopology().getBrokers().stream()
                      .filter(b -> brokerId.equals(b.getId()))
                      .findFirst()
                      .orElseThrow();
              assertThat(broker.getPartitions())
                  .as("the added zone's broker hosts all partitions")
                  .extracting(PartitionState::getId)
                  .containsExactlyInAnyOrder(1, 2);
            });
  }

  /**
   * Asserts that every partition of {@code physicalTenantId} is assigned according to {@code
   * zones}: each zone holds exactly its configured {@link Zone#numberOfReplicas()} replicas of
   * every partition, the preferred leader sits in the highest-priority zone, and within a zone the
   * tenant's partitions are spread over that zone's brokers.
   *
   * <p>The preferred leader is the replica holding Raft priority {@code replicationFactor}, which
   * is the highest priority {@link
   * io.camunda.zeebe.dynamic.config.util.ZoneAwarePartitionDistributor} hands out and which it
   * always gives to a broker in the highest-priority zone.
   *
   * <p>Reads the assignment from the physical-tenant-scoped cluster topology, so it describes what
   * the cluster configuration decided. Use {@link #assertLeadersInZone} for what the brokers
   * actually run.
   *
   * <p>The spread check is a balance property (no broker in a zone holds more than one replica more
   * than another), so it only discriminates where a zone has more than one broker and the tenant
   * has enough partitions to distribute over them; for a single-broker zone, or a tenant with a
   * single partition, it is satisfied trivially.
   */
  public static void assertPartitionsAssignedPerZoneLayout(
      final ClusterActuator actuator,
      final String physicalTenantId,
      final List<Zone> zones,
      final int partitionCount,
      final int replicationFactor) {
    final var highestPriorityZone =
        zones.stream().max(Comparator.comparingInt(Zone::priority)).orElseThrow().name();

    Awaitility.await("physical tenant '%s' is assigned per zone layout".formatted(physicalTenantId))
        .atMost(ASSERTION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var replicas = replicasByPartition(actuator, physicalTenantId);
              assertThat(replicas.keySet())
                  .as("physical tenant '%s' has all its partitions assigned", physicalTenantId)
                  .containsExactlyInAnyOrderElementsOf(partitionIds(partitionCount));

              replicas.forEach(
                  (partitionId, members) -> {
                    zones.forEach(
                        zone ->
                            assertThat(membersInZone(members.keySet(), zone.name()))
                                .as(
                                    "partition %d of physical tenant '%s' has %d replica(s) in"
                                        + " zone '%s'",
                                    partitionId,
                                    physicalTenantId,
                                    zone.numberOfReplicas(),
                                    zone.name())
                                .hasSize(zone.numberOfReplicas()));

                    assertThat(preferredLeaders(members, replicationFactor))
                        .as(
                            "partition %d of physical tenant '%s' prefers a leader in the"
                                + " highest-priority zone '%s'",
                            partitionId, physicalTenantId, highestPriorityZone)
                        .singleElement()
                        .matches(member -> member.isInZone(highestPriorityZone));
                  });

              zones.forEach(
                  zone -> {
                    final var replicasPerBroker = replicasPerBrokerInZone(replicas, zone);
                    final var counts = replicasPerBroker.values();
                    assertThat(max(counts) - min(counts))
                        .as(
                            "physical tenant '%s' spreads its partitions over the brokers of zone"
                                + " '%s', but they are assigned as %s",
                            physicalTenantId, zone.name(), replicasPerBroker)
                        .isLessThanOrEqualTo(1);
                  });
            });
  }

  /**
   * Asserts that every partition of {@code physicalTenantId} has exactly one leader and that the
   * leader runs on a broker in {@code zone}.
   *
   * <p>The runtime counterpart to {@link #assertPartitionsAssignedPerZoneLayout}: it queries each
   * broker's own view of the partitions it runs for that physical tenant, so it fails if the
   * assignment never materialised into elected Raft leaders.
   */
  public static void assertLeadersInZone(
      final TestCluster cluster,
      final String physicalTenantId,
      final String zone,
      final int partitionCount) {
    assertLeaders(
        cluster,
        physicalTenantId,
        partitionCount,
        member -> member.isInZone(zone),
        "in zone '%s'".formatted(zone));
  }

  /**
   * Asserts that every partition of {@code physicalTenantId} has exactly one leader and that none
   * of them run in {@code excludedZone}.
   *
   * <p>For asserting re-election after {@code excludedZone} is killed: with a zone-aware,
   * priority-ranked distributor there is no fixed zone to require the new leader in, since which of
   * the surviving zones wins a given partition's election is not pinned by this test (only that the
   * dead zone cannot).
   */
  public static void assertLeadersOutsideZone(
      final TestCluster cluster,
      final String physicalTenantId,
      final String excludedZone,
      final int partitionCount) {
    assertLeaders(
        cluster,
        physicalTenantId,
        partitionCount,
        member -> !member.isInZone(excludedZone),
        "outside zone '%s'".formatted(excludedZone));
  }

  /**
   * Rebalances {@code cluster} until every leader of {@code physicalTenantId} sits in {@code zone},
   * re-triggering the best-effort rebalance on each poll.
   *
   * <p>Re-adding a zone, or raising its priority, does not by itself displace a healthy sitting
   * leader elsewhere - only an explicit rebalance issues {@code stepDownIfNotPrimary} so the
   * highest-priority zone's replica wins re-election, mirroring {@code
   * ZoneAwareClusterEndpointIT#shouldSwapZonePrioritiesAndMoveLeaders}. Unlike {@link
   * #assertLeadersInZone}, this drives that rebalance itself rather than assuming leadership has
   * already settled.
   *
   * <p>Takes the broker map explicitly, rather than a {@link TestCluster}, because {@link
   * #startBrokerInZone} deliberately starts a replacement broker outside the cluster's own
   * bookkeeping - callers combine the cluster's surviving brokers with any such replacement
   * themselves.
   */
  public static void awaitLeadersInZoneAfterRebalance(
      final Map<MemberId, TestStandaloneBroker> brokers,
      final RebalanceActuator rebalanceActuator,
      final String physicalTenantId,
      final String zone,
      final int partitionCount) {
    Awaitility.await(
            "physical tenant '%s' moves every leader to zone '%s' after rebalancing"
                .formatted(physicalTenantId, zone))
        .atMost(ASSERTION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              rebalanceActuator.rebalance();
              final var leaders = leadersByPartition(brokers, physicalTenantId);
              assertThat(leaders)
                  .as("every partition of physical tenant '%s' has a leader", physicalTenantId)
                  .containsOnlyKeys(partitionIds(partitionCount));
              leaders.forEach(
                  (partitionId, members) ->
                      assertThat(members)
                          .as(
                              "partition %d of physical tenant '%s' has exactly one leader, in"
                                  + " zone '%s'",
                              partitionId, physicalTenantId, zone)
                          .singleElement()
                          .matches(member -> member.isInZone(zone)));
            });
  }

  private static void assertLeaders(
      final TestCluster cluster,
      final String physicalTenantId,
      final int partitionCount,
      final Predicate<MemberId> leaderMatcher,
      final String matcherDescription) {
    Awaitility.await(
            "physical tenant '%s' elected every leader %s"
                .formatted(physicalTenantId, matcherDescription))
        .atMost(ASSERTION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var leaders = leadersByPartition(cluster.brokers(), physicalTenantId);
              assertThat(leaders)
                  .as("every partition of physical tenant '%s' has a leader", physicalTenantId)
                  .containsOnlyKeys(partitionIds(partitionCount));
              leaders.forEach(
                  (partitionId, members) ->
                      assertThat(members)
                          .as(
                              "partition %d of physical tenant '%s' has exactly one leader, %s",
                              partitionId, physicalTenantId, matcherDescription)
                          .singleElement()
                          .matches(leaderMatcher));
            });
  }

  /** The members of {@code zone} in {@code cluster}, started or not. */
  public static Set<MemberId> membersOfZone(final TestCluster cluster, final String zone) {
    return cluster.brokers().keySet().stream()
        .filter(member -> member.isInZone(zone))
        .collect(Collectors.toSet());
  }

  /** The members holding each of {@code physicalTenantId}'s partitions, keyed by partition id. */
  private static Map<Integer, Map<MemberId, PartitionState>> replicasByPartition(
      final ClusterActuator actuator, final String physicalTenantId) {
    final Map<Integer, Map<MemberId, PartitionState>> replicas = new HashMap<>();
    // a physical-tenant-scoped topology already reports only that tenant's partition group, so
    // every partition below belongs to it
    for (final var broker : actuator.getTopology(physicalTenantId).getBrokers()) {
      final var member = memberIdOf(broker.getId());
      broker
          .getPartitions()
          .forEach(
              partition ->
                  replicas
                      .computeIfAbsent(partition.getId(), id -> new HashMap<>())
                      .put(member, partition));
    }
    return replicas;
  }

  /**
   * A reported broker id as a {@link MemberId}. Zone-aware clusters report the string form ({@code
   * zone-a_0}), non-zone-aware ones a bare integer; both are handled so a mismatch cannot surface
   * as a {@link ClassCastException} swallowed by an {@code ignoreExceptions} await.
   */
  private static MemberId memberIdOf(final BrokerId brokerId) {
    return switch (brokerId) {
      case final BrokerId.String s -> MemberId.from(s.value());
      case final BrokerId.Integer i -> MemberId.from(String.valueOf(i.value()));
      default ->
          throw new IllegalStateException("unexpected broker id type: " + brokerId.getClass());
    };
  }

  /**
   * The members currently leading each of {@code physicalTenantId}'s partitions.
   *
   * <p>Skips brokers that are not currently started: a stopped broker's actuator is unreachable,
   * and this is queried while a zone may be deliberately down, not only when every broker is up.
   */
  private static Map<Integer, List<MemberId>> leadersByPartition(
      final Map<MemberId, TestStandaloneBroker> brokers, final String physicalTenantId) {
    final Map<Integer, List<MemberId>> leaders = new HashMap<>();
    brokers.entrySet().stream()
        .filter(entry -> entry.getValue().isStarted())
        .forEach(
            entry -> {
              final var member = entry.getKey();
              PartitionsActuator.of(entry.getValue())
                  .query(physicalTenantId)
                  .forEach(
                      (partitionId, status) -> {
                        if ("Leader".equalsIgnoreCase(status.role())) {
                          leaders.computeIfAbsent(partitionId, id -> new ArrayList<>()).add(member);
                        }
                      });
            });
    return leaders;
  }

  /** How many of the tenant's partitions each broker of {@code zone} holds a replica of. */
  private static Map<MemberId, Long> replicasPerBrokerInZone(
      final Map<Integer, Map<MemberId, PartitionState>> replicas, final Zone zone) {
    final Map<MemberId, Long> replicasPerBroker = new HashMap<>();
    for (int localNodeIdx = 0; localNodeIdx < zone.numberOfBrokers(); localNodeIdx++) {
      final var member = MemberId.from(zone.name(), localNodeIdx);
      replicasPerBroker.put(
          member, replicas.values().stream().filter(m -> m.containsKey(member)).count());
    }
    return replicasPerBroker;
  }

  private static List<MemberId> preferredLeaders(
      final Map<MemberId, PartitionState> members, final int replicationFactor) {
    return members.entrySet().stream()
        .filter(entry -> Objects.equals(entry.getValue().getPriority(), replicationFactor))
        .map(Map.Entry::getKey)
        .toList();
  }

  private static List<MemberId> membersInZone(final Set<MemberId> members, final String zone) {
    return members.stream().filter(member -> member.isInZone(zone)).toList();
  }

  private static List<Integer> partitionIds(final int partitionCount) {
    return IntStream.rangeClosed(1, partitionCount).boxed().toList();
  }

  private static long max(final Collection<Long> counts) {
    return counts.stream().mapToLong(Long::longValue).max().orElse(0);
  }

  private static long min(final Collection<Long> counts) {
    return counts.stream().mapToLong(Long::longValue).min().orElse(0);
  }

  /**
   * start a cluster over {@code initialZones}, add a broker in {@code newZone}, then update the
   * partition distribution to {@code targetZones}.
   */
  public record AddZoneScenario(
      String clusterName, List<Zone> initialZones, List<Zone> targetZones, String newZone) {
    @Override
    public String toString() {
      return clusterName;
    }
  }
}
