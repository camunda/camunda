/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.protocol.model.TopologyResponse;
import jakarta.annotation.Generated;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedTopologyResponseMapper {

  private GeneratedTopologyResponseMapper() {}

  public static TopologyResponse toProtocol(final GeneratedTopologyResponseStrictContract source) {
    return new TopologyResponse()
        .brokers(
            source.brokers() == null
                ? null
                : source.brokers().stream().map(GeneratedBrokerInfoMapper::toProtocol).toList())
        .clusterId(source.clusterId())
        .clusterSize(source.clusterSize())
        .partitionsCount(source.partitionsCount())
        .replicationFactor(source.replicationFactor())
        .gatewayVersion(source.gatewayVersion())
        .lastCompletedChangeId(source.lastCompletedChangeId());
  }
}
