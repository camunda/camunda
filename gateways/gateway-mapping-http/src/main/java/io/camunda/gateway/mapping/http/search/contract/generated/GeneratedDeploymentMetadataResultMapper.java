/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.protocol.model.DeploymentMetadataResult;
import jakarta.annotation.Generated;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedDeploymentMetadataResultMapper {

  private GeneratedDeploymentMetadataResultMapper() {}

  public static DeploymentMetadataResult toProtocol(
      final GeneratedDeploymentMetadataStrictContract source) {
    return new DeploymentMetadataResult()
        .processDefinition(
            source.processDefinition() == null
                ? null
                : GeneratedDeploymentProcessResultMapper.toProtocol(source.processDefinition()))
        .decisionDefinition(
            source.decisionDefinition() == null
                ? null
                : GeneratedDeploymentDecisionResultMapper.toProtocol(source.decisionDefinition()))
        .decisionRequirements(
            source.decisionRequirements() == null
                ? null
                : GeneratedDeploymentDecisionRequirementsResultMapper.toProtocol(
                    source.decisionRequirements()))
        .form(
            source.form() == null
                ? null
                : GeneratedDeploymentFormResultMapper.toProtocol(source.form()))
        .resource(
            source.resource() == null
                ? null
                : GeneratedDeploymentResourceResultMapper.toProtocol(source.resource()));
  }
}
