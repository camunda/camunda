/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.protocol.model.DeploymentDecisionRequirementsResult;
import jakarta.annotation.Generated;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedDeploymentDecisionRequirementsResultMapper {

  private GeneratedDeploymentDecisionRequirementsResultMapper() {}

  public static DeploymentDecisionRequirementsResult toProtocol(
      final GeneratedDeploymentDecisionRequirementsStrictContract source) {
    return new DeploymentDecisionRequirementsResult()
        .decisionRequirementsId(source.decisionRequirementsId())
        .decisionRequirementsName(source.decisionRequirementsName())
        .version(source.version())
        .resourceName(source.resourceName())
        .tenantId(source.tenantId())
        .decisionRequirementsKey(source.decisionRequirementsKey());
  }
}
