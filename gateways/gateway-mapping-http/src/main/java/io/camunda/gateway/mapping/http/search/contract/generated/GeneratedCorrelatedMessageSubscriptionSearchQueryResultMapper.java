/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.protocol.model.CorrelatedMessageSubscriptionSearchQueryResult;
import jakarta.annotation.Generated;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedCorrelatedMessageSubscriptionSearchQueryResultMapper {

  private GeneratedCorrelatedMessageSubscriptionSearchQueryResultMapper() {}

  public static CorrelatedMessageSubscriptionSearchQueryResult toProtocol(
      final GeneratedCorrelatedMessageSubscriptionSearchQueryStrictContract source) {
    return new CorrelatedMessageSubscriptionSearchQueryResult()
        .items(
            source.items() == null
                ? null
                : source.items().stream()
                    .map(GeneratedCorrelatedMessageSubscriptionResultMapper::toProtocol)
                    .toList());
  }
}
