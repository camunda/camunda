/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.management;

import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.gateway.admin.BrokerAdminRequest;
import io.camunda.zeebe.shared.management.RebalancingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class BrokerRebalancingService implements RebalancingService {

  private final SpringBrokerBridge bridge;

  @Autowired
  public BrokerRebalancingService(final SpringBrokerBridge bridge) {
    this.bridge = bridge;
  }

  @Override
  public void rebalanceCluster() {
    final var client =
        bridge
            .getBrokerClient()
            .orElseThrow(() -> new IllegalStateException("No broker client available"));
    client
        .getTopologyManager()
        .getTopology()
        .getPartitions()
        .forEach(
            (partition) -> {
              final var request = new BrokerAdminRequest();
              request.setPartitionId(partition);
              request.stepDownIfNotPrimary();
              client.sendRequest(request);
            });
  }
}
