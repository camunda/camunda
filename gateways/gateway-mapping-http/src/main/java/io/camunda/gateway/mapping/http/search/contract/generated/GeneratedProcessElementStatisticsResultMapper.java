/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.protocol.model.ProcessElementStatisticsResult;
import jakarta.annotation.Generated;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedProcessElementStatisticsResultMapper {

  private GeneratedProcessElementStatisticsResultMapper() {}

  public static ProcessElementStatisticsResult toProtocol(
      final GeneratedProcessElementStatisticsStrictContract source) {
    return new ProcessElementStatisticsResult()
        .elementId(source.elementId())
        .active(source.active())
        .canceled(source.canceled())
        .incidents(source.incidents())
        .completed(source.completed());
  }
}
