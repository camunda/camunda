/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.protocol.model.EvaluateDecisionResult;
import jakarta.annotation.Generated;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedEvaluateDecisionResultMapper {

  private GeneratedEvaluateDecisionResultMapper() {}

  public static EvaluateDecisionResult toProtocol(
      final GeneratedEvaluateDecisionStrictContract source) {
    return new EvaluateDecisionResult()
        .decisionDefinitionId(source.decisionDefinitionId())
        .decisionDefinitionKey(source.decisionDefinitionKey())
        .decisionDefinitionName(source.decisionDefinitionName())
        .decisionDefinitionVersion(source.decisionDefinitionVersion())
        .decisionEvaluationKey(source.decisionEvaluationKey())
        .decisionInstanceKey(source.decisionInstanceKey())
        .decisionRequirementsId(source.decisionRequirementsId())
        .decisionRequirementsKey(source.decisionRequirementsKey())
        .evaluatedDecisions(
            source.evaluatedDecisions() == null
                ? null
                : source.evaluatedDecisions().stream()
                    .map(GeneratedEvaluatedDecisionResultMapper::toProtocol)
                    .toList())
        .failedDecisionDefinitionId(source.failedDecisionDefinitionId())
        .failureMessage(source.failureMessage())
        .output(source.output())
        .tenantId(source.tenantId());
  }
}
