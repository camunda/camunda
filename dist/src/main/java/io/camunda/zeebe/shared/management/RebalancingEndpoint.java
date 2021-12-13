/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.zeebe.gateway.Loggers;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.stereotype.Component;

@Component
@WebEndpoint(id = "rebalance")
public final class RebalancingEndpoint {
  private static final Logger LOG = Loggers.GATEWAY_LOGGER;

  private final RebalancingService service;

  @Autowired
  public RebalancingEndpoint(final RebalancingService service) {
    this.service = service;
  }

  @WriteOperation
  public WebEndpointResponse<Void> rebalance() {
    LOG.info("Rebalancing leaders of all partitions");
    service.rebalanceCluster();
    return new WebEndpointResponse<>();
  }
}
