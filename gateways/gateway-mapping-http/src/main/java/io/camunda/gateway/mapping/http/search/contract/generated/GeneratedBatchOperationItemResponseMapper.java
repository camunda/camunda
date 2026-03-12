/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.protocol.model.BatchOperationItemResponse;
import jakarta.annotation.Generated;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedBatchOperationItemResponseMapper {

  private GeneratedBatchOperationItemResponseMapper() {}

  public static BatchOperationItemResponse toProtocol(
      final GeneratedBatchOperationItemResponseStrictContract source) {
    return new BatchOperationItemResponse()
        .operationType(source.operationType())
        .batchOperationKey(source.batchOperationKey())
        .itemKey(source.itemKey())
        .processInstanceKey(source.processInstanceKey())
        .rootProcessInstanceKey(source.rootProcessInstanceKey())
        .state(
            source.state() == null
                ? null
                : BatchOperationItemResponse.StateEnum.fromValue(source.state()))
        .processedDate(source.processedDate())
        .errorMessage(source.errorMessage());
  }
}
