/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.protocol.model.DecisionInstanceResult;
import jakarta.annotation.Generated;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedDecisionInstanceResultMapper {

  private GeneratedDecisionInstanceResultMapper() {}

  public static DecisionInstanceResult toProtocol(
      final GeneratedDecisionInstanceStrictContract source) {
    return new DecisionInstanceResult()
        .decisionDefinitionId(source.decisionDefinitionId())
        .decisionDefinitionKey(source.decisionDefinitionKey())
        .decisionDefinitionName(source.decisionDefinitionName())
        .decisionDefinitionType(source.decisionDefinitionType())
        .decisionDefinitionVersion(source.decisionDefinitionVersion())
        .decisionEvaluationInstanceKey(source.decisionEvaluationInstanceKey())
        .decisionEvaluationKey(source.decisionEvaluationKey())
        .elementInstanceKey(source.elementInstanceKey())
        .evaluationDate(source.evaluationDate())
        .evaluationFailure(source.evaluationFailure())
        .processDefinitionKey(source.processDefinitionKey())
        .processInstanceKey(source.processInstanceKey())
        .result(source.result())
        .rootDecisionDefinitionKey(source.rootDecisionDefinitionKey())
        .rootProcessInstanceKey(source.rootProcessInstanceKey())
        .state(source.state())
        .tenantId(source.tenantId());
  }
}
