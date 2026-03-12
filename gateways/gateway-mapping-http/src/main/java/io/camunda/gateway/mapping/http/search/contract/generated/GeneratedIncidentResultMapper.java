/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.protocol.model.IncidentResult;
import jakarta.annotation.Generated;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedIncidentResultMapper {

  private GeneratedIncidentResultMapper() {}

  public static IncidentResult toProtocol(final GeneratedIncidentStrictContract source) {
    return new IncidentResult()
        .processDefinitionId(source.processDefinitionId())
        .errorType(source.errorType())
        .errorMessage(source.errorMessage())
        .elementId(source.elementId())
        .creationTime(source.creationTime())
        .state(source.state())
        .tenantId(source.tenantId())
        .incidentKey(source.incidentKey())
        .processDefinitionKey(source.processDefinitionKey())
        .processInstanceKey(source.processInstanceKey())
        .rootProcessInstanceKey(source.rootProcessInstanceKey())
        .elementInstanceKey(source.elementInstanceKey())
        .jobKey(source.jobKey());
  }
}
