/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.protocol.model.BatchOperationResponse;
import jakarta.annotation.Generated;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedBatchOperationResponseMapper {

  private GeneratedBatchOperationResponseMapper() {}

  public static BatchOperationResponse toProtocol(
      final GeneratedBatchOperationResponseStrictContract source) {
    return new BatchOperationResponse()
        .batchOperationKey(source.batchOperationKey())
        .state(source.state())
        .batchOperationType(source.batchOperationType())
        .startDate(source.startDate())
        .endDate(source.endDate())
        .actorType(source.actorType())
        .actorId(source.actorId())
        .operationsTotalCount(source.operationsTotalCount())
        .operationsFailedCount(source.operationsFailedCount())
        .operationsCompletedCount(source.operationsCompletedCount())
        .errors(
            source.errors() == null
                ? null
                : source.errors().stream()
                    .map(GeneratedBatchOperationErrorMapper::toProtocol)
                    .toList());
  }
}
