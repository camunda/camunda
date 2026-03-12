/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.protocol.model.UsageMetricsResponse;
import jakarta.annotation.Generated;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedUsageMetricsResponseMapper {

  private GeneratedUsageMetricsResponseMapper() {}

  public static UsageMetricsResponse toProtocol(
      final GeneratedUsageMetricsResponseStrictContract source) {
    return new UsageMetricsResponse()
        .activeTenants(source.activeTenants())
        .tenants(
            source.tenants() == null
                ? null
                : source.tenants().entrySet().stream()
                    .collect(
                        java.util.stream.Collectors.toMap(
                            java.util.Map.Entry::getKey,
                            e ->
                                GeneratedUsageMetricsResponseItemMapper.toProtocol(e.getValue()))));
  }
}
