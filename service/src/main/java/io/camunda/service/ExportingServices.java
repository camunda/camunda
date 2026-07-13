/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.service.management.PhysicalTenantManagementService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerAdminRequest;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExportingServices extends PhysicalTenantManagementService<ExportingServices> {
  private static final Logger LOG = LoggerFactory.getLogger(ExportingServices.class);

  public ExportingServices(
      final String physicalTenantId,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        physicalTenantId,
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  public CompletableFuture<Void> pauseExporting() {
    LOG.info("Pausing exporting on all partitions.");
    return broadcastPartitionMembers(
            (partitionId, brokerId) -> {
              final var request = new BrokerAdminRequest();
              request.setPartitionId(partitionId);
              request.setBrokerId(brokerId);
              request.pauseExporting();
              return request;
            })
        .thenApply(responses -> null);
  }

  public CompletableFuture<Void> softPauseExporting() {
    LOG.info("Soft Pausing exporting on all partitions.");
    return broadcastPartitionMembers(
            (partitionId, brokerId) -> {
              final var request = new BrokerAdminRequest();
              request.setPartitionId(partitionId);
              request.setBrokerId(brokerId);
              request.softPauseExporting();
              return request;
            })
        .thenApply(responses -> null);
  }

  public CompletableFuture<Void> resumeExporting() {
    LOG.info("Resuming exporting on all partitions.");
    return broadcastPartitionMembers(
            (partitionId, brokerId) -> {
              final var request = new BrokerAdminRequest();
              request.setPartitionId(partitionId);
              request.setBrokerId(brokerId);
              request.resumeExporting();
              return request;
            })
        .thenApply(responses -> null);
  }
}
