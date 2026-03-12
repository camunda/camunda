/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.protocol.model.BrokerInfo;
import jakarta.annotation.Generated;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedBrokerInfoMapper {

  private GeneratedBrokerInfoMapper() {}

  public static BrokerInfo toProtocol(final GeneratedBrokerInfoStrictContract source) {
    return new BrokerInfo()
        .nodeId(source.nodeId())
        .host(source.host())
        .port(source.port())
        .partitions(
            source.partitions() == null
                ? null
                : source.partitions().stream().map(GeneratedPartitionMapper::toProtocol).toList())
        .version(source.version());
  }
}
