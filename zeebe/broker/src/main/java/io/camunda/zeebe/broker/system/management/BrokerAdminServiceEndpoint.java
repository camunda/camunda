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
import java.util.Map;
import java.util.Optional;
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
   * Triggers the given admin operation. Without the {@code physicalTenant} query parameter, this
   * broker-local operation applies to the default physical tenant's partitions, matching today's
   * behavior. With the parameter, it applies to the given physical tenant's partitions instead.
   */
  @WriteOperation
  public Map<Integer, PartitionStatus> trigger(
      @Selector final String operation, @Nullable final String physicalTenant) {
    final var consumer = operations.get(operation);
    if (consumer == null) {
      // Not a valid operation
      return null;
    }
    final var adminService = resolveAdminService(physicalTenant);
    adminService.ifPresent(consumer);
    return partitionStatus(physicalTenant);
  }

  @ReadOperation
  public Map<Integer, PartitionStatus> partitionStatus(@Nullable final String physicalTenant) {
    return resolveAdminService(physicalTenant)
        .map(BrokerAdminService::getPartitionStatus)
        .orElse(Map.of());
  }

  @ReadOperation
  public Optional<PartitionStatus> singlePartition(
      @Selector final Integer partitionId, @Nullable final String physicalTenant) {
    return Optional.ofNullable(partitionStatus(physicalTenant).get(partitionId));
  }

  private Optional<BrokerAdminService> resolveAdminService(@Nullable final String physicalTenant) {
    final var physicalTenantId =
        physicalTenant != null && !physicalTenant.isBlank()
            ? physicalTenant
            : DEFAULT_PHYSICAL_TENANT_ID;
    return springBrokerBridge.getAdminService(physicalTenantId);
  }
}
