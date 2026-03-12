/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.protocol.model.ElementInstanceResult;
import jakarta.annotation.Generated;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedElementInstanceResultMapper {

  private GeneratedElementInstanceResultMapper() {}

  public static ElementInstanceResult toProtocol(
      final GeneratedElementInstanceStrictContract source) {
    return new ElementInstanceResult()
        .processDefinitionId(source.processDefinitionId())
        .startDate(source.startDate())
        .endDate(source.endDate())
        .elementId(source.elementId())
        .elementName(source.elementName())
        .type(
            source.type() == null ? null : ElementInstanceResult.TypeEnum.fromValue(source.type()))
        .state(source.state())
        .hasIncident(source.hasIncident())
        .tenantId(source.tenantId())
        .elementInstanceKey(source.elementInstanceKey())
        .processInstanceKey(source.processInstanceKey())
        .rootProcessInstanceKey(source.rootProcessInstanceKey())
        .processDefinitionKey(source.processDefinitionKey())
        .incidentKey(source.incidentKey());
  }
}
