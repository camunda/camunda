/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.management;

import io.zeebe.broker.SpringBrokerBridge;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.stereotype.Component;

@Component
@Endpoint(id = "partitions")
public class BrokerAdminServiceEndpoint {

  @Autowired private SpringBrokerBridge springBrokerBridge;

  private final Map<String, Runnable> operations = new HashMap<>();

  public BrokerAdminServiceEndpoint() {
    operations.put("pauseProcessing", this::pauseProcessing);
    operations.put("resumeProcessing", this::resumeProcessing);
    operations.put("takeSnapshot", this::takeSnapshot);
    operations.put("prepareUpgrade", this::prepareUpgrade);
    operations.put("pauseExporting", this::pauseExporting);
    operations.put("resumeExporting", this::resumeExporting);
  }

  @WriteOperation
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
    springBrokerBridge.getAdminService().ifPresent(BrokerAdminService::pauseStreamProcessing);
    return partitionStatus();
  }

  private Map<Integer, PartitionStatus> resumeProcessing() {
    springBrokerBridge.getAdminService().ifPresent(BrokerAdminService::resumeStreamProcessing);
    return partitionStatus();
  }

  private Map<Integer, PartitionStatus> pauseExporting() {
    springBrokerBridge.getAdminService().ifPresent(BrokerAdminService::pauseExporting);
    return partitionStatus();
  }

  private Map<Integer, PartitionStatus> resumeExporting() {
    springBrokerBridge.getAdminService().ifPresent(BrokerAdminService::resumeExporting);
    return partitionStatus();
  }

  private Map<Integer, PartitionStatus> takeSnapshot() {
    springBrokerBridge.getAdminService().ifPresent(BrokerAdminService::takeSnapshot);
    return partitionStatus();
  }

  private Map<Integer, PartitionStatus> prepareUpgrade() {
    springBrokerBridge.getAdminService().ifPresent(BrokerAdminService::prepareForUpgrade);
    return partitionStatus();
  }

  @ReadOperation
  public Map<Integer, PartitionStatus> partitionStatus() {
    return springBrokerBridge
        .getAdminService()
        .map(BrokerAdminService::getPartitionStatus)
        .orElse(Map.of());
  }
}
