/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.atomix.cluster.BrokerMemberId;
import io.atomix.cluster.MemberId;
import io.camunda.service.TopologyServices.Partition;
import io.camunda.service.TopologyServices.Topology;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reports the topology of the whole cluster, aggregated over all physical tenants (ADR 001 D5,
 * {@code docs/adr/management/001-physical-tenant-health-status-topology.md}).
 *
 * <p>Cluster-level facts ({@code brokers}, {@code clusterId}, {@code clusterSize}, {@code
 * gatewayVersion}) are reported once; per-physical-tenant facts (partition count, replication
 * factor, last completed change id, and per-broker partition role/health/state) are reported per
 * physical tenant. This is the cluster-admin authenticated surface where physical tenant ids are
 * exposed; {@code GET /cluster/v2/status} is deliberately the tenant-id-free public counterpart.
 */
@NullMarked
public final class ClusterTopologyServices {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClusterTopologyServices.class);

  private final Map<String, TopologyServices> topologyServicesByPhysicalTenant;

  public ClusterTopologyServices(
      final Map<String, TopologyServices> topologyServicesByPhysicalTenant) {
    this.topologyServicesByPhysicalTenant = Map.copyOf(topologyServicesByPhysicalTenant);
  }

  /**
   * @return the cluster-wide topology, folded overall known physical tenants in sorted tenant-id
   *     order. A physical tenant whose future fails is skipped; the overall future never fails.
   */
  public CompletableFuture<ClusterTopology> getTopology() {
    final var futuresByTenantId =
        new LinkedHashMap<String, CompletableFuture<@Nullable Topology>>();
    topologyServicesByPhysicalTenant.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry -> {
              final var tenantId = entry.getKey();
              futuresByTenantId.put(
                  tenantId,
                  entry
                      .getValue()
                      .getTopology()
                      .exceptionally(
                          error -> {
                            LOGGER.debug(
                                "Topology of physical tenant '{}' is unavailable", tenantId, error);
                            return null;
                          }));
            });

    return CompletableFuture.allOf(futuresByTenantId.values().toArray(CompletableFuture[]::new))
        .thenApply(ignored -> aggregate(futuresByTenantId));
  }

  private static ClusterTopology aggregate(
      final Map<String, CompletableFuture<@Nullable Topology>> futuresByTenantId) {
    final var surviving = new LinkedHashMap<String, Topology>();
    futuresByTenantId.forEach(
        (tenantId, future) -> {
          final var topology = future.join();
          if (topology != null) {
            surviving.put(tenantId, topology);
          }
        });

    final var physicalTenants =
        surviving.entrySet().stream()
            .map(entry -> toPhysicalTenantTopology(entry.getKey(), entry.getValue()))
            .toList();

    final var initialized =
        surviving.values().stream().filter(ClusterTopologyServices::isInitialized);

    final String clusterId =
        initialized
            .map(Topology::clusterId)
            .map(id -> id == null || id.isEmpty() ? null : id)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    final Integer clusterSize =
        surviving.values().stream()
            .filter(ClusterTopologyServices::isInitialized)
            .map(Topology::clusterSize)
            .findFirst()
            .orElse(0);
    final String gatewayVersion =
        surviving.values().stream()
            .map(Topology::gatewayVersion)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);

    return new ClusterTopology(
        unionBrokers(surviving.values()), clusterId, clusterSize, gatewayVersion, physicalTenants);
  }

  private static boolean isInitialized(final Topology topology) {
    return topology.clusterSize() != null && topology.clusterSize() >= 0;
  }

  private static PhysicalTenantTopology toPhysicalTenantTopology(
      final String physicalTenantId, final Topology topology) {
    final var brokers =
        topology.brokers().stream()
            .sorted(
                Comparator.comparing(
                    broker -> broker.brokerId().memberId(), MemberId.ID_COMPARATOR))
            .map(broker -> new PhysicalTenantBroker(broker.brokerId(), broker.partitions()))
            .toList();
    return new PhysicalTenantTopology(
        physicalTenantId,
        topology.partitionsCount(),
        topology.replicationFactor(),
        topology.lastCompletedChangeId(),
        brokers);
  }

  private static List<ClusterBroker> unionBrokers(final Collection<Topology> topologies) {
    final Map<BrokerMemberId, ClusterBroker> byBrokerId = new LinkedHashMap<>();
    topologies.forEach(
        topology ->
            topology
                .brokers()
                .forEach(
                    broker ->
                        byBrokerId.putIfAbsent(
                            broker.brokerId(),
                            new ClusterBroker(
                                broker.brokerId(),
                                broker.host(),
                                broker.port(),
                                broker.version()))));
    return byBrokerId.values().stream()
        .sorted(
            Comparator.comparing(broker -> broker.brokerId().memberId(), MemberId.ID_COMPARATOR))
        .toList();
  }

  public record ClusterTopology(
      List<ClusterBroker> brokers,
      @Nullable String clusterId,
      Integer clusterSize,
      @Nullable String gatewayVersion,
      List<PhysicalTenantTopology> physicalTenants) {}

  public record ClusterBroker(BrokerMemberId brokerId, String host, Integer port, String version) {}

  public record PhysicalTenantTopology(
      String physicalTenantId,
      Integer partitionsCount,
      Integer replicationFactor,
      Long lastCompletedChangeId,
      List<PhysicalTenantBroker> brokers) {}

  public record PhysicalTenantBroker(BrokerMemberId brokerId, List<Partition> partitions) {}
}
