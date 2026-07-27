/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.management;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

import io.camunda.zeebe.broker.SpringBrokerBridge;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
@Endpoint(id = "partitions")
public class BrokerAdminServiceEndpoint {

  @Autowired private SpringBrokerBridge springBrokerBridge;

  private final Map<String, java.util.function.Consumer<BrokerAdminService>> operations =
      new HashMap<>();

  public BrokerAdminServiceEndpoint() {
    operations.put("pauseProcessing", BrokerAdminService::pauseStreamProcessing);
    operations.put("resumeProcessing", BrokerAdminService::resumeStreamProcessing);
    operations.put("takeSnapshot", BrokerAdminService::takeSnapshot);
    operations.put("pauseExporting", BrokerAdminService::pauseExporting);
    operations.put("softPauseExporting", BrokerAdminService::softPauseExporting);
    operations.put("resumeExporting", BrokerAdminService::resumeExporting);
  }

  /**
   * Triggers the given admin operation. With the {@code physicalTenant} query parameter, this
   * broker-local operation applies only to the given physical tenant's partitions. Without it, it
   * applies to every physical tenant known on this node (Node scope, per ADR 003 D3); the response
   * is keyed by physical tenant ID unless there is at most one known physical tenant, in which case
   * it keeps today's flat, single-tenant shape.
   */
  @WriteOperation
  public Object trigger(@Selector final String operation, @Nullable final String physicalTenant) {
    final var consumer = operations.get(operation);
    if (consumer == null) {
      // Not a valid operation
      return null;
    }
    final var tenant = normalize(physicalTenant);
    if (tenant.isPresent()) {
      springBrokerBridge.getAdminService(tenant.get()).ifPresent(consumer);
      return partitionStatusForTenant(tenant.get());
    }
    final var tenantIds = tenantIds();
    tenantIds.forEach(id -> springBrokerBridge.getAdminService(id).ifPresent(consumer));
    return aggregatedPartitionStatus(tenantIds);
  }

  /**
   * Returns the partition status. With the {@code physicalTenant} query parameter, scoped to that
   * physical tenant's partitions only (flat, keyed by partition ID, matching today's shape).
   * Without it, returns every physical tenant known on this node (Node scope), keyed by physical
   * tenant ID unless there is at most one known physical tenant, in which case it keeps today's
   * flat, single-tenant shape.
   */
  @ReadOperation
  public Object partitionStatus(@Nullable final String physicalTenant) {
    final var tenant = normalize(physicalTenant);
    if (tenant.isPresent()) {
      return partitionStatusForTenant(tenant.get());
    }
    return aggregatedPartitionStatus(tenantIds());
  }

  /**
   * Returns the status of a single partition. With the {@code physicalTenant} query parameter,
   * resolves the partition within that physical tenant's group. Without it, and if more than one
   * physical tenant is known on this node, the partition ID alone is ambiguous (it aliases across
   * physical tenant groups), so the result is keyed by physical tenant ID instead of returning a
   * single status.
   */
  @ReadOperation
  public Object singlePartition(
      @Selector final Integer partitionId, @Nullable final String physicalTenant) {
    final var tenant = normalize(physicalTenant);
    if (tenant.isPresent()) {
      return Optional.ofNullable(partitionStatusForTenant(tenant.get()).get(partitionId));
    }
    final var tenantIds = tenantIds();
    if (tenantIds.size() <= 1) {
      final var tenantId = tenantIds.stream().findFirst().orElse(DEFAULT_PHYSICAL_TENANT_ID);
      return Optional.ofNullable(partitionStatusForTenant(tenantId).get(partitionId));
    }
    final Map<String, PartitionStatus> byTenant = new LinkedHashMap<>();
    for (final var id : tenantIds) {
      final var status = partitionStatusForTenant(id).get(partitionId);
      if (status != null) {
        byTenant.put(id, status);
      }
    }
    return byTenant;
  }

  /**
   * Returns the flat, per-partition status map for a single physical tenant, matching today's
   * shape.
   */
  private Map<Integer, PartitionStatus> partitionStatusForTenant(final String physicalTenantId) {
    return springBrokerBridge
        .getAdminService(physicalTenantId)
        .map(BrokerAdminService::getPartitionStatus)
        .orElse(Map.of());
  }

  /**
   * Aggregates the partition status across every given physical tenant. If there is at most one
   * known physical tenant, returns the flat, single-tenant shape for full backward compatibility;
   * otherwise, keys the result by physical tenant ID since partition IDs alias across physical
   * tenant groups.
   */
  private Object aggregatedPartitionStatus(final Set<String> tenantIds) {
    if (tenantIds.size() <= 1) {
      final var tenantId = tenantIds.stream().findFirst().orElse(DEFAULT_PHYSICAL_TENANT_ID);
      return partitionStatusForTenant(tenantId);
    }
    final Map<String, Map<Integer, PartitionStatus>> byTenant = new LinkedHashMap<>();
    for (final var id : tenantIds) {
      byTenant.put(id, partitionStatusForTenant(id));
    }
    return byTenant;
  }

  private Set<String> tenantIds() {
    return springBrokerBridge.getBrokerAdminServiceTenantIds();
  }

  private Optional<String> normalize(@Nullable final String physicalTenant) {
    return Optional.ofNullable(physicalTenant).filter(t -> !t.isBlank());
  }
}
