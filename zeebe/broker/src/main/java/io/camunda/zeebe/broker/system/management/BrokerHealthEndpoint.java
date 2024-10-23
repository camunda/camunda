/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.management;

import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

@Component
@Endpoint(id = "brokerHealth")
public class BrokerHealthEndpoint {

  @Autowired SpringBrokerBridge springBrokerBridge;

  @ReadOperation
  public CompletableFuture<HealthTree> brokerHealth() {
    return springBrokerBridge
        .getBrokerHealthCheckService()
        .map(BrokerHealthCheckService::getHealthReport)
        .map(ActorFuture::toCompletableFuture)
        .map(f -> f.thenApply(HealthTree::fromHealthReport))
        .orElse(null);
  }
}
