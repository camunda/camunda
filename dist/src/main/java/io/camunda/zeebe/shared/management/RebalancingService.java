/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.admin.BrokerAdminRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class RebalancingService {

  private final BrokerClient client;

  @Autowired
  public RebalancingService(final BrokerClient client) {
    this.client = client;
  }

  public void rebalanceCluster() {
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
