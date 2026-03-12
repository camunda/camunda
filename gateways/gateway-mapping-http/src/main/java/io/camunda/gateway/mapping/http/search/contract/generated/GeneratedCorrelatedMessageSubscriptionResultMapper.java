/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.protocol.model.CorrelatedMessageSubscriptionResult;
import jakarta.annotation.Generated;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedCorrelatedMessageSubscriptionResultMapper {

  private GeneratedCorrelatedMessageSubscriptionResultMapper() {}

  public static CorrelatedMessageSubscriptionResult toProtocol(
      final GeneratedCorrelatedMessageSubscriptionStrictContract source) {
    return new CorrelatedMessageSubscriptionResult()
        .correlationKey(source.correlationKey())
        .correlationTime(source.correlationTime())
        .elementId(source.elementId())
        .elementInstanceKey(source.elementInstanceKey())
        .messageKey(source.messageKey())
        .messageName(source.messageName())
        .partitionId(source.partitionId())
        .processDefinitionId(source.processDefinitionId())
        .processDefinitionKey(source.processDefinitionKey())
        .processInstanceKey(source.processInstanceKey())
        .rootProcessInstanceKey(source.rootProcessInstanceKey())
        .subscriptionKey(source.subscriptionKey())
        .tenantId(source.tenantId());
  }
}
