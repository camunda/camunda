/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.protocol.model.EvaluatedDecisionResult;
import jakarta.annotation.Generated;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedEvaluatedDecisionResultMapper {

  private GeneratedEvaluatedDecisionResultMapper() {}

  public static EvaluatedDecisionResult toProtocol(
      final GeneratedEvaluatedDecisionStrictContract source) {
    return new EvaluatedDecisionResult()
        .decisionDefinitionId(source.decisionDefinitionId())
        .decisionDefinitionName(source.decisionDefinitionName())
        .decisionDefinitionVersion(source.decisionDefinitionVersion())
        .decisionDefinitionType(source.decisionDefinitionType())
        .output(source.output())
        .tenantId(source.tenantId())
        .matchedRules(
            source.matchedRules() == null
                ? null
                : source.matchedRules().stream()
                    .map(GeneratedMatchedDecisionRuleItemMapper::toProtocol)
                    .toList())
        .evaluatedInputs(
            source.evaluatedInputs() == null
                ? null
                : source.evaluatedInputs().stream()
                    .map(GeneratedEvaluatedDecisionInputItemMapper::toProtocol)
                    .toList())
        .decisionDefinitionKey(source.decisionDefinitionKey())
        .decisionEvaluationInstanceKey(source.decisionEvaluationInstanceKey());
  }
}
