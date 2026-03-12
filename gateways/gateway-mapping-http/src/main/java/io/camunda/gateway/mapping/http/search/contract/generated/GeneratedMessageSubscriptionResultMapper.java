/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.protocol.model.MessageSubscriptionResult;
import jakarta.annotation.Generated;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedMessageSubscriptionResultMapper {

  private GeneratedMessageSubscriptionResultMapper() {}

  public static MessageSubscriptionResult toProtocol(
      final GeneratedMessageSubscriptionStrictContract source) {
    return new MessageSubscriptionResult()
        .messageSubscriptionKey(source.messageSubscriptionKey())
        .processDefinitionId(source.processDefinitionId())
        .processDefinitionKey(source.processDefinitionKey())
        .processInstanceKey(source.processInstanceKey())
        .rootProcessInstanceKey(source.rootProcessInstanceKey())
        .elementId(source.elementId())
        .elementInstanceKey(source.elementInstanceKey())
        .messageSubscriptionState(source.messageSubscriptionState())
        .lastUpdatedDate(source.lastUpdatedDate())
        .messageName(source.messageName())
        .correlationKey(source.correlationKey())
        .tenantId(source.tenantId());
  }
}
