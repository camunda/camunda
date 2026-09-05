/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.actuator;

import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import feign.Body;
import feign.Contract;
import feign.Feign;
import feign.Headers;
import feign.Param;
import feign.RequestLine;
import feign.Retryer;
import feign.Target.HardCodedTarget;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import io.camunda.container.cluster.BrokerNode;
import io.camunda.zeebe.management.cluster.AddZoneRequest;
import io.camunda.zeebe.management.cluster.BrokerId;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequest;
import io.camunda.zeebe.management.cluster.ClusterZoneMigrationRequest;
import io.camunda.zeebe.management.cluster.ConfigurationChange;
import io.camunda.zeebe.management.cluster.GetConfigurationChangesResponse;
import io.camunda.zeebe.management.cluster.GetTopologyResponse;
import io.camunda.zeebe.management.cluster.PartitionDistributionConfig;
import io.camunda.zeebe.management.cluster.PlannedOperationsResponse;
import io.camunda.zeebe.management.cluster.RoutingState;
import io.camunda.zeebe.management.cluster.UpdatePartitionDistributionRequest;
import io.camunda.zeebe.qa.util.cluster.TestApplication;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import java.util.List;

public interface ClusterActuator {

  /**
   * Returns a {@link ClusterActuator} instance using the given node as upstream.
   *
   * @param node the node to connect to
   * @return a new instance of {@link ClusterActuator}
   */
  static ClusterActuator of(final BrokerNode<?> node) {
    return ofAddress(node.getExternalMonitoringAddress());
  }

  /**
   * Returns a {@link ClusterActuator} instance using the given node as upstream.
   *
   * @param node the node to connect to
   * @return a new instance of {@link ClusterActuator}
   */
  static ClusterActuator of(final TestApplication<?> node) {
    return of(node.actuatorUri("cluster").toString());
  }

  /**
   * Returns a {@link ClusterActuator} instance using the given address as upstream.
   *
   * @param address the monitoring address
   * @return a new instance of {@link ClusterActuator}
   */
  static ClusterActuator ofAddress(final String address) {
    final var endpoint = String.format("http://%s/actuator/cluster", address);
    return of(endpoint);
  }

  /**
   * Returns a {@link ClusterActuator} instance using the given endpoint as upstream.
   *
   * @param endpoint the endpoint to connect to
   * @return a new instance of {@link ClusterActuator}
   */
  static ClusterActuator of(final String endpoint) {
    final var target = new HardCodedTarget<>(ClusterActuator.class, endpoint);
    // Custom contract that disables slash decoding in URL templates, so that
    // zone-aware broker IDs like "zoneA%2F0" are preserved in path parameters.
    final Contract defaultContract = new Contract.Default();
    final Contract noDecodeSlash =
        targetType -> {
          final var metadata = defaultContract.parseAndValidateMetadata(targetType);
          metadata.forEach(md -> md.template().decodeSlash(false));
          return metadata;
        };
    return Feign.builder()
        .contract(noDecodeSlash)
        .encoder(new JacksonEncoder(List.of(new Jdk8Module(), new JavaTimeModule())))
        .decoder(new JacksonDecoder(List.of(new Jdk8Module(), new JavaTimeModule())))
        .retryer(Retryer.NEVER_RETRY)
        // The default http client do not support http PATCH
        .client(new feign.httpclient.ApacheHttpClient())
        .target(target);
  }

  /**
   * Request that the broker joins the partition with the given priority.
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("POST /brokers/{brokerId}/partitions/{partitionId}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  @Body("%7B\"priority\": {priority}%7D")
  PlannedOperationsResponse joinPartition(
      @Param final String brokerId, @Param final int partitionId, @Param final int priority);

  /**
   * Request that the broker joins the partition with the given priority.
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("POST /brokers/{brokerId}/partitions/{partitionId}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  @Body("%7B\"priority\": {priority}%7D")
  PlannedOperationsResponse joinPartition(
      @Param final int brokerId, @Param final int partitionId, @Param final int priority);

  /**
   * Request that the broker joins the partition with the given priority.
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("POST /brokers/{brokerId}/partitions/{partitionId}?dryRun={dryRun}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  @Body("%7B\"priority\": {priority}%7D")
  PlannedOperationsResponse joinPartition(
      @Param final String brokerId,
      @Param final int partitionId,
      @Param final int priority,
      @Param boolean dryRun);

  /**
   * Request that the broker joins the partition with the given priority.
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("POST /brokers/{brokerId}/partitions/{partitionId}?dryRun={dryRun}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  @Body("%7B\"priority\": {priority}%7D")
  PlannedOperationsResponse joinPartition(
      @Param final int brokerId,
      @Param final int partitionId,
      @Param final int priority,
      @Param boolean dryRun);

  /**
   * Request that the broker joins the given physical tenant's partition with the given priority.
   * Partition ids restart at 1 in every physical tenant, so the partition is only identified by the
   * two together.
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx), notably 404 if
   *     the physical tenant is unknown
   */
  @RequestLine("POST /brokers/{brokerId}/partitions/{partitionId}?physicalTenant={physicalTenant}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  @Body("%7B\"priority\": {priority}%7D")
  PlannedOperationsResponse joinPartition(
      @Param final int brokerId,
      @Param final int partitionId,
      @Param final int priority,
      @Param final String physicalTenant);

  /**
   * Request that the broker leaves the partition.
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("DELETE /brokers/{brokerId}/partitions/{partitionId}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  PlannedOperationsResponse leavePartition(
      @Param final String brokerId, @Param final int partitionId);

  /**
   * Request that the broker leaves the partition.
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("DELETE /brokers/{brokerId}/partitions/{partitionId}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  PlannedOperationsResponse leavePartition(@Param final int brokerId, @Param final int partitionId);

  /**
   * Request that the broker leaves the given physical tenant's partition. Partition ids restart at
   * 1 in every physical tenant, so the partition is only identified by the two together.
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx), notably 404 if
   *     the physical tenant is unknown
   */
  @RequestLine(
      "DELETE /brokers/{brokerId}/partitions/{partitionId}?physicalTenant={physicalTenant}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  PlannedOperationsResponse leavePartition(
      @Param final int brokerId, @Param final int partitionId, @Param final String physicalTenant);

  /**
   * Queries the current cluster topology
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("GET")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  GetTopologyResponse getTopology();

  /**
   * Queries the current cluster topology, scoped to the given physical tenant.
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx), notably 404 if
   *     the physical tenant is unknown
   */
  @RequestLine("GET ?physicalTenant={physicalTenant}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  GetTopologyResponse getTopology(@Param final String physicalTenant);

  /**
   * Scales the given brokers up or down and reassigns partitions to the new brokers.
   *
   * @param ids
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("POST /brokers")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  PlannedOperationsResponse scaleBrokers(@RequestBody List<Integer> ids);

  /**
   * Scales the given brokers up or down and reassigns partitions to the new brokers based on new
   * replication factor.
   *
   * @param ids
   * @param newReplicationFactor new replication factor after scaling, if <=0 use the current value
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("POST /brokers?replicationFactor={newReplicationFactor}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  PlannedOperationsResponse scaleBrokers(
      @RequestBody List<Integer> ids, @Param final int newReplicationFactor);

  /**
   * Scales the given brokers up or down and reassigns partitions to the new brokers.
   *
   * @param dryRun if true, changes are not applied but only simulated.
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("POST /brokers?dryRun={dryRun}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  PlannedOperationsResponse scaleBrokers(@RequestBody List<Integer> ids, @Param boolean dryRun);

  /**
   * Scales the given brokers up or down and reassigns partitions to the new brokers.
   *
   * @param dryRun if true, changes are not applied but only simulated.
   * @param force if true, the brokers that are not specified will be forcely removed.
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("POST /brokers?dryRun={dryRun}&force={force}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  PlannedOperationsResponse scaleBrokers(
      @RequestBody List<Integer> ids, @Param boolean dryRun, @Param boolean force);

  /**
   * Scales the given brokers up or down and reassigns partitions to the new brokers.
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("POST /brokers")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  PlannedOperationsResponse scaleBrokersString(@RequestBody List<String> ids);

  /**
   * Scales the given brokers up or down and reassigns partitions to the new brokers based on new
   * replication factor.
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("POST /brokers?replicationFactor={newReplicationFactor}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  PlannedOperationsResponse scaleBrokersString(
      @RequestBody List<String> ids, @Param final int newReplicationFactor);

  /**
   * Scales the given brokers up or down and reassigns partitions to the new brokers.
   *
   * @param dryRun if true, changes are not applied but only simulated.
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("POST /brokers?dryRun={dryRun}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  PlannedOperationsResponse scaleBrokersString(
      @RequestBody List<String> ids, @Param boolean dryRun);

  /**
   * Scales the given brokers up or down and reassigns partitions to the new brokers.
   *
   * @param dryRun if true, changes are not applied but only simulated.
   * @param force if true, the brokers that are not specified will be forcely removed.
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("POST /brokers?dryRun={dryRun}&force={force}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  PlannedOperationsResponse scaleBrokersString(
      @RequestBody List<String> ids, @Param boolean dryRun, @Param boolean force);

  /**
   * Request that the broker is added to the cluster.
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("POST /brokers/{brokerId}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  PlannedOperationsResponse addBroker(@Param final String brokerId);

  /**
   * Request that the broker is added to the cluster.
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("POST /brokers/{brokerId}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  PlannedOperationsResponse addBroker(@Param final int brokerId);

  /**
   * Request that the broker is added to the cluster.
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("POST /brokers/{brokerId}?dryRun={dryRun}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  PlannedOperationsResponse addBroker(@Param final String brokerId, @Param boolean dryRun);

  /**
   * Request that the broker is added to the cluster.
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("POST /brokers/{brokerId}?dryRun={dryRun}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  PlannedOperationsResponse addBroker(@Param final int brokerId, @Param boolean dryRun);

  /**
   * Request that the broker is removed from the cluster
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("DELETE /brokers/{brokerId}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  PlannedOperationsResponse removeBroker(@Param final String brokerId);

  /**
   * Request that the broker is removed from the cluster
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("DELETE /brokers/{brokerId}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  PlannedOperationsResponse removeBroker(@Param final int brokerId);

  @RequestLine("DELETE /changes/{changeId}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  GetTopologyResponse cancelChange(@Param final long changeId);

  /**
   * Returns the status and operations of one configuration change - the pending plan if {@code
   * changeId} matches it, otherwise the last completed change.
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx), notably 404 if
   *     no change with this id is known
   */
  @RequestLine("GET /changes/{changeId}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  ConfigurationChange getChange(@Param final long changeId);

  /**
   * Lists every configuration change currently known to this broker (the pending plan, if any, and
   * the last completed change).
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("GET /changes")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  GetConfigurationChangesResponse getChanges();

  // invalid parameter types
  @RequestLine("POST /brokers")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  PlannedOperationsResponse scaleBrokersInvalidType(@RequestBody List<String> ids);

  /**
   * Request that the broker is added to the cluster.
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("POST /brokers/{brokerId}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  PlannedOperationsResponse addBrokerInvalidType(@Param final String brokerId);

  /**
   * Scales the given brokers up or down and reassigns partitions to the new brokers.
   *
   * @param dryRun if true, changes are not applied but only simulated.
   * @param force if true, the brokers that are not specified will be forcely removed.
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("PATCH ?dryRun={dryRun}&force={force}")
  @Headers({"Content-Type: application/json", "accept: application/json"})
  PlannedOperationsResponse patchCluster(
      @RequestBody final ClusterConfigPatchRequest request,
      @Param boolean dryRun,
      @Param boolean force);

  /**
   * Scopes the partition count change carried by {@code request} to a single physical tenant's
   * partition group instead of the default one; every other physical tenant is left untouched.
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx), notably 404 if
   *     the physical tenant is unknown, and 400 if {@code request} also changes cluster membership
   *     or the replication factor, neither of which has a tenant dimension
   */
  @RequestLine("PATCH ?dryRun={dryRun}&force={force}&physicalTenant={physicalTenant}")
  @Headers({"Content-Type: application/json", "accept: application/json"})
  PlannedOperationsResponse patchCluster(
      @RequestBody final ClusterConfigPatchRequest request,
      @Param boolean dryRun,
      @Param boolean force,
      @Param final String physicalTenant);

  @RequestLine("POST /purge?dryRun={dryRun}")
  @Headers({"Content-Type: application/json"})
  PlannedOperationsResponse purge(@Param boolean dryRun);

  /** Purges only the given physical tenant, leaving the other physical tenants untouched. */
  @RequestLine("POST /purge?dryRun={dryRun}&physicalTenant={physicalTenant}")
  @Headers({"Content-Type: application/json"})
  PlannedOperationsResponse purge(@Param boolean dryRun, @Param String physicalTenant);

  @RequestLine("PATCH /routing-state?dryRun={dryRun}&force={force}")
  @Headers({"Content-Type: application/json", "accept: application/json"})
  void patchRoutingState(@RequestBody final RoutingState routingState, @Param boolean dryRun);

  @RequestLine("PATCH /routing-state?dryRun={dryRun}&force={force}")
  @Headers({"Content-Type: application/json", "accept: application/json"})
  void patchRoutingState(@Param boolean dryRun);

  /**
   * Writes the routing state scoped to {@code physicalTenant}, either with the given body or, when
   * {@code routingState} is omitted, by fetching it from the engine.
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx), notably 404 if
   *     the physical tenant is unknown
   */
  @RequestLine("PATCH /routing-state?dryRun={dryRun}&physicalTenant={physicalTenant}")
  @Headers({"Content-Type: application/json", "accept: application/json"})
  void patchRoutingState(
      @RequestBody final RoutingState routingState,
      @Param boolean dryRun,
      @Param final String physicalTenant);

  /**
   * Fetches the routing state from the engine and writes it scoped to {@code physicalTenant}.
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx), notably 404 if
   *     the physical tenant is unknown
   */
  @RequestLine("PATCH /routing-state?dryRun={dryRun}&physicalTenant={physicalTenant}")
  @Headers({"Content-Type: application/json", "accept: application/json"})
  void patchRoutingState(@Param boolean dryRun, @Param final String physicalTenant);

  @RequestLine("PUT /partition-distribution?dryRun={dryRun}")
  @Headers({"Content-Type: application/json", "accept: application/json"})
  PlannedOperationsResponse updatePartitionDistribution(
      @RequestBody final UpdatePartitionDistributionRequest request, @Param boolean dryRun);

  /** Applies a full partition distribution config via {@code PUT /partition-distribution}. */
  default PlannedOperationsResponse patchPartitionDistribution(
      final PartitionDistributionConfig config, final boolean dryRun) {
    return updatePartitionDistribution(
        new UpdatePartitionDistributionRequest().config(config), dryRun);
  }

  /**
   * Performs a leader switchover via {@code PUT /partition-distribution}: re-orders the existing
   * per-zone priorities by {@code zonePriorities} (highest first).
   */
  default PlannedOperationsResponse updateZonePriorities(
      final List<String> zonePriorities, final boolean dryRun) {
    return updatePartitionDistribution(
        new UpdatePartitionDistributionRequest().zonePriorities(zonePriorities), dryRun);
  }

  @RequestLine("PUT /zones?dryRun={dryRun}")
  @Headers({"Content-Type: application/json", "accept: application/json"})
  PlannedOperationsResponse migrateZone(
      @RequestBody final ClusterZoneMigrationRequest request, @Param boolean dryRun);

  /**
   * Force-removes the given zone: force-evicts the zone's brokers and drops the zone from the
   * partition distribution config.
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("DELETE /zones/{zoneId}?dryRun={dryRun}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  PlannedOperationsResponse forceRemoveZone(@Param final String zoneId, @Param boolean dryRun);

  /**
   * Adds back the given zone: re-adds the given brokers and re-includes the zone in the partition
   * distribution config.
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("POST /zones/{zoneId}?dryRun={dryRun}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  PlannedOperationsResponse addZone(
      @Param final String zoneId, @RequestBody final AddZoneRequest request, @Param boolean dryRun);

  // -- BrokerId dispatch methods (default) --

  /**
   * Dispatches to {@link #joinPartition(int, int, int)} or {@link #joinPartition(String, int,
   * int)}.
   */
  default PlannedOperationsResponse joinPartition(
      final BrokerId brokerId, final int partitionId, final int priority) {
    return switch (brokerId) {
      case final BrokerId.Integer i -> joinPartition(i.value(), partitionId, priority);
      case final BrokerId.String s -> joinPartition(s.value(), partitionId, priority);
    };
  }

  /**
   * Dispatches to {@link #joinPartition(int, int, int, boolean)} or {@link #joinPartition(String,
   * int, int, boolean)}.
   */
  default PlannedOperationsResponse joinPartition(
      final BrokerId brokerId, final int partitionId, final int priority, final boolean dryRun) {
    return switch (brokerId) {
      case final BrokerId.Integer i -> joinPartition(i.value(), partitionId, priority, dryRun);
      case final BrokerId.String s -> joinPartition(s.value(), partitionId, priority, dryRun);
    };
  }

  /** Dispatches to {@link #leavePartition(int, int)} or {@link #leavePartition(String, int)}. */
  default PlannedOperationsResponse leavePartition(final BrokerId brokerId, final int partitionId) {
    return switch (brokerId) {
      case final BrokerId.Integer i -> leavePartition(i.value(), partitionId);
      case final BrokerId.String s -> leavePartition(s.value(), partitionId);
    };
  }

  /** Dispatches to {@link #addBroker(int)} or {@link #addBroker(String)}. */
  default PlannedOperationsResponse addBroker(final BrokerId brokerId) {
    return switch (brokerId) {
      case final BrokerId.Integer i -> addBroker(i.value());
      case final BrokerId.String s -> addBroker(s.value());
    };
  }

  /** Dispatches to {@link #addBroker(int, boolean)} or {@link #addBroker(String, boolean)}. */
  default PlannedOperationsResponse addBroker(final BrokerId brokerId, final boolean dryRun) {
    return switch (brokerId) {
      case final BrokerId.Integer i -> addBroker(i.value(), dryRun);
      case final BrokerId.String s -> addBroker(s.value(), dryRun);
    };
  }

  /** Dispatches to {@link #removeBroker(int)} or {@link #removeBroker(String)}. */
  default PlannedOperationsResponse removeBroker(final BrokerId brokerId) {
    return switch (brokerId) {
      case final BrokerId.Integer i -> removeBroker(i.value());
      case final BrokerId.String s -> removeBroker(s.value());
    };
  }

  /** Dispatches to {@link #scaleBrokers(List)} or {@link #scaleBrokersString(List)}. */
  default PlannedOperationsResponse scaleByBrokerIds(final List<BrokerId> ids) {
    if (ids.isEmpty() || ids.getFirst() instanceof BrokerId.Integer) {
      return scaleBrokers(ids.stream().map(b -> ((BrokerId.Integer) b).value()).toList());
    }
    return scaleBrokersString(ids.stream().map(b -> ((BrokerId.String) b).value()).toList());
  }

  /**
   * Dispatches to {@link #scaleBrokers(List, boolean)} or {@link #scaleBrokersString(List,
   * boolean)}.
   */
  default PlannedOperationsResponse scaleByBrokerIds(
      final List<BrokerId> ids, final boolean dryRun) {
    if (ids.isEmpty() || ids.getFirst() instanceof BrokerId.Integer) {
      return scaleBrokers(ids.stream().map(b -> ((BrokerId.Integer) b).value()).toList(), dryRun);
    }
    return scaleBrokersString(
        ids.stream().map(b -> ((BrokerId.String) b).value()).toList(), dryRun);
  }

  /**
   * Dispatches to {@link #scaleBrokers(List, boolean, boolean)} or {@link #scaleBrokersString(List,
   * boolean, boolean)}.
   */
  default PlannedOperationsResponse scaleByBrokerIds(
      final List<BrokerId> ids, final boolean dryRun, final boolean force) {
    if (ids.isEmpty() || ids.getFirst() instanceof BrokerId.Integer) {
      return scaleBrokers(
          ids.stream().map(b -> ((BrokerId.Integer) b).value()).toList(), dryRun, force);
    }
    return scaleBrokersString(
        ids.stream().map(b -> ((BrokerId.String) b).value()).toList(), dryRun, force);
  }
}
