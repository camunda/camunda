/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.rest;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;

@ZeebeRestController
public final class TopologyController {
  private final BrokerClient client;

  @Autowired
  public TopologyController(final BrokerClient client) {
    this.client = client;
  }

  @GetMapping(path = "/topology", produces = "application/json")
  public BrokerClusterState get() {
    return client.getTopologyManager().getTopology();
  }
}
