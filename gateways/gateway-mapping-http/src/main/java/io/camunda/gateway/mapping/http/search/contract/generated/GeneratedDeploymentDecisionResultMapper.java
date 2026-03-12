/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.protocol.model.DeploymentDecisionResult;
import jakarta.annotation.Generated;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedDeploymentDecisionResultMapper {

  private GeneratedDeploymentDecisionResultMapper() {}

  public static DeploymentDecisionResult toProtocol(
      final GeneratedDeploymentDecisionStrictContract source) {
    return new DeploymentDecisionResult()
        .decisionDefinitionId(source.decisionDefinitionId())
        .version(source.version())
        .name(source.name())
        .tenantId(source.tenantId())
        .decisionRequirementsId(source.decisionRequirementsId())
        .decisionDefinitionKey(source.decisionDefinitionKey())
        .decisionRequirementsKey(source.decisionRequirementsKey());
  }
}
