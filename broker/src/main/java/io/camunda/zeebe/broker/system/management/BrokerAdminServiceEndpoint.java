/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.management;

import io.camunda.zeebe.broker.MicronautBrokerBridge;
import io.micronaut.context.annotation.Bean;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.micronaut.management.endpoint.annotation.Selector;
import io.micronaut.management.endpoint.annotation.Write;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Singleton
@Endpoint(id = "partitions")
public class BrokerAdminServiceEndpoint {

  private final MicronautBrokerBridge micronautBrokerBridge;

  private final Map<String, Runnable> operations = new HashMap<>();

  public BrokerAdminServiceEndpoint(final MicronautBrokerBridge micronautBrokerBridge) {
    this.micronautBrokerBridge = micronautBrokerBridge;
    operations.put("pauseProcessing", this::pauseProcessing);
    operations.put("resumeProcessing", this::resumeProcessing);
    operations.put("takeSnapshot", this::takeSnapshot);
    operations.put("prepareUpgrade", this::prepareUpgrade);
    operations.put("pauseExporting", this::pauseExporting);
    operations.put("resumeExporting", this::resumeExporting);
  }

  @Write
  public Map<Integer, PartitionStatus> trigger(@Selector final String operation) {
    final var runnable = operations.get(operation);
    if (runnable != null) {
      runnable.run();
      return partitionStatus();
    }
    // Not a valid operation
    return null;
  }

  private Map<Integer, PartitionStatus> pauseProcessing() {
    micronautBrokerBridge.getAdminService().ifPresent(BrokerAdminService::pauseStreamProcessing);
    return partitionStatus();
  }

  private Map<Integer, PartitionStatus> resumeProcessing() {
    micronautBrokerBridge.getAdminService().ifPresent(BrokerAdminService::resumeStreamProcessing);
    return partitionStatus();
  }

  private Map<Integer, PartitionStatus> pauseExporting() {
    micronautBrokerBridge.getAdminService().ifPresent(BrokerAdminService::pauseExporting);
    return partitionStatus();
  }

  private Map<Integer, PartitionStatus> resumeExporting() {
    micronautBrokerBridge.getAdminService().ifPresent(BrokerAdminService::resumeExporting);
    return partitionStatus();
  }

  private Map<Integer, PartitionStatus> takeSnapshot() {
    micronautBrokerBridge.getAdminService().ifPresent(BrokerAdminService::takeSnapshot);
    return partitionStatus();
  }

  private Map<Integer, PartitionStatus> prepareUpgrade() {
    micronautBrokerBridge.getAdminService().ifPresent(BrokerAdminService::prepareForUpgrade);
    return partitionStatus();
  }

  @Read
  public Map<Integer, PartitionStatus> partitionStatus() {
    return micronautBrokerBridge
        .getAdminService()
        .map(BrokerAdminService::getPartitionStatus)
        .orElse(Map.of());
  }
}
