/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.protocol.model.GlobalJobStatisticsQueryResult;
import jakarta.annotation.Generated;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedGlobalJobStatisticsQueryResultMapper {

  private GeneratedGlobalJobStatisticsQueryResultMapper() {}

  public static GlobalJobStatisticsQueryResult toProtocol(
      final GeneratedGlobalJobStatisticsQueryStrictContract source) {
    return new GlobalJobStatisticsQueryResult()
        .created(GeneratedStatusMetricMapper.toProtocol(source.created()))
        .completed(GeneratedStatusMetricMapper.toProtocol(source.completed()))
        .failed(GeneratedStatusMetricMapper.toProtocol(source.failed()))
        .isIncomplete(source.isIncomplete());
  }
}
