/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.management;

import io.camunda.zeebe.broker.SpringBrokerBridge;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.stereotype.Component;

@Component
@Endpoint(id = "partitionHealth")
public class PartitionHealthEndpoint {

  @Autowired private SpringBrokerBridge springBrokerBridge;

  @ReadOperation
  public Map<Integer, HealthTree> partitionHealth() {
    return springBrokerBridge
        .getAdminService()
        .map(BrokerAdminService::getPartitionHealth)
        .map(
            map ->
                map.entrySet().stream()
                    .collect(
                        Collectors.toMap(
                            Map.Entry::getKey, e -> HealthTree.fromHealthReport(e.getValue()))))
        .orElse(Map.of());
  }

  @ReadOperation
  public Optional<HealthTree> partitionHealth(@Selector final Integer partitionId) {
    return springBrokerBridge
        .getAdminService()
        .flatMap(service -> Optional.ofNullable(service.getPartitionHealth().get(partitionId)))
        .map(HealthTree::fromHealthReport);
  }
}
