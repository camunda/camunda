/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.protocol.model.ProcessInstanceResult;
import jakarta.annotation.Generated;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedProcessInstanceResultMapper {

  private GeneratedProcessInstanceResultMapper() {}

  public static ProcessInstanceResult toProtocol(
      final GeneratedProcessInstanceStrictContract source) {
    return new ProcessInstanceResult()
        .processDefinitionId(source.processDefinitionId())
        .processDefinitionName(source.processDefinitionName())
        .processDefinitionVersion(source.processDefinitionVersion())
        .processDefinitionVersionTag(source.processDefinitionVersionTag())
        .startDate(source.startDate())
        .endDate(source.endDate())
        .state(source.state())
        .hasIncident(source.hasIncident())
        .tenantId(source.tenantId())
        .processInstanceKey(source.processInstanceKey())
        .processDefinitionKey(source.processDefinitionKey())
        .parentProcessInstanceKey(source.parentProcessInstanceKey())
        .parentElementInstanceKey(source.parentElementInstanceKey())
        .rootProcessInstanceKey(source.rootProcessInstanceKey())
        .tags(source.tags())
        .businessId(source.businessId());
  }
}
