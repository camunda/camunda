/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.ResponseMapper.formatDate;
import static io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionInstanceStrictContract.Fields;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionInstanceStrictContract;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.protocol.model.DecisionDefinitionTypeEnum;
import io.camunda.gateway.protocol.model.DecisionInstanceGetQueryResult;
import io.camunda.gateway.protocol.model.DecisionInstanceStateEnum;
import io.camunda.gateway.protocol.model.EvaluatedDecisionInputItem;
import io.camunda.gateway.protocol.model.EvaluatedDecisionOutputItem;
import io.camunda.gateway.protocol.model.MatchedDecisionRuleItem;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceInputEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceOutputEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Contract adaptation layer for decision instance response projections.
 *
 * <p>Policy in this adapter decides which projection to emit by operation context: search emits the
 * base projection while get enriches with evaluated inputs and matched rules.
 */
public final class DecisionInstanceContractAdapter {

  private DecisionInstanceContractAdapter() {}

  public static List<GeneratedDecisionInstanceStrictContract> toSearchProjections(
      final List<DecisionInstanceEntity> instances) {
    return instances.stream().map(DecisionInstanceContractAdapter::toSearchProjection).toList();
  }

  public static GeneratedDecisionInstanceStrictContract toSearchProjection(
      final DecisionInstanceEntity entity) {
    return toStrictContract(entity);
  }

  public static DecisionInstanceGetQueryResult toGetProjection(
      final DecisionInstanceEntity entity) {
    final var strictContractView = toStrictContract(entity);
    return new DecisionInstanceGetQueryResult()
        .decisionEvaluationKey(strictContractView.decisionEvaluationKey())
        .decisionEvaluationInstanceKey(strictContractView.decisionEvaluationInstanceKey())
        .state(strictContractView.state())
        .evaluationDate(strictContractView.evaluationDate())
        .evaluationFailure(strictContractView.evaluationFailure())
        .processDefinitionKey(strictContractView.processDefinitionKey())
        .processInstanceKey(strictContractView.processInstanceKey())
        .rootProcessInstanceKey(strictContractView.rootProcessInstanceKey())
        .elementInstanceKey(strictContractView.elementInstanceKey())
        .decisionDefinitionKey(strictContractView.decisionDefinitionKey())
        .decisionDefinitionId(strictContractView.decisionDefinitionId())
        .decisionDefinitionName(strictContractView.decisionDefinitionName())
        .decisionDefinitionVersion(strictContractView.decisionDefinitionVersion())
        .decisionDefinitionType(strictContractView.decisionDefinitionType())
        .rootDecisionDefinitionKey(strictContractView.rootDecisionDefinitionKey())
        .result(strictContractView.result())
        .evaluatedInputs(toEvaluatedInputs(entity.evaluatedInputs()))
        .matchedRules(toMatchedRules(entity.evaluatedOutputs()))
        .tenantId(strictContractView.tenantId());
  }

  private static GeneratedDecisionInstanceStrictContract toStrictContract(
      final DecisionInstanceEntity entity) {
    return GeneratedDecisionInstanceStrictContract.builder()
        .decisionDefinitionId(
            ContractPolicy.requireNonNull(
                entity.decisionDefinitionId(), Fields.DECISION_DEFINITION_ID, entity))
        .decisionDefinitionKey(
            ContractPolicy.requireNonNull(
                entity.decisionDefinitionKey(), Fields.DECISION_DEFINITION_KEY, entity))
        .decisionDefinitionName(
            ContractPolicy.requireNonNull(
                entity.decisionDefinitionName(), Fields.DECISION_DEFINITION_NAME, entity))
        .decisionDefinitionType(
            ContractPolicy.requireNonNull(
                toDecisionDefinitionTypeEnum(entity.decisionDefinitionType()),
                Fields.DECISION_DEFINITION_TYPE,
                entity))
        .decisionDefinitionVersion(
            ContractPolicy.requireNonNull(
                entity.decisionDefinitionVersion(), Fields.DECISION_DEFINITION_VERSION, entity))
        .decisionEvaluationInstanceKey(
            ContractPolicy.requireNonNull(
                entity.decisionInstanceId(), Fields.DECISION_EVALUATION_INSTANCE_KEY, entity))
        .decisionEvaluationKey(
            ContractPolicy.requireNonNull(
                entity.decisionInstanceKey(), Fields.DECISION_EVALUATION_KEY, entity))
        .evaluationDate(
            ContractPolicy.requireNonNull(
                formatDate(entity.evaluationDate()), Fields.EVALUATION_DATE, entity))
        .result(ContractPolicy.requireNonNull(entity.result(), Fields.RESULT, entity))
        .rootDecisionDefinitionKey(
            ContractPolicy.requireNonNull(
                entity.rootDecisionDefinitionKey(), Fields.ROOT_DECISION_DEFINITION_KEY, entity))
        .state(
            ContractPolicy.requireNonNull(
                toDecisionInstanceStateEnum(entity.state()), Fields.STATE, entity))
        .tenantId(ContractPolicy.requireNonNull(entity.tenantId(), Fields.TENANT_ID, entity))
        .elementInstanceKey(entity.flowNodeInstanceKey())
        .evaluationFailure(entity.evaluationFailure())
        .processDefinitionKey(entity.processDefinitionKey())
        .processInstanceKey(entity.processInstanceKey())
        .rootProcessInstanceKey(entity.rootProcessInstanceKey())
        .build();
  }

  private static List<EvaluatedDecisionInputItem> toEvaluatedInputs(
      final List<DecisionInstanceInputEntity> decisionInstanceInputEntities) {
    if (decisionInstanceInputEntities == null) {
      return null;
    }
    return decisionInstanceInputEntities.stream()
        .map(
            input ->
                new EvaluatedDecisionInputItem()
                    .inputId(input.inputId())
                    .inputName(input.inputName())
                    .inputValue(input.inputValue()))
        .toList();
  }

  private static List<MatchedDecisionRuleItem> toMatchedRules(
      final List<DecisionInstanceOutputEntity> decisionInstanceOutputEntities) {
    if (decisionInstanceOutputEntities == null) {
      return null;
    }
    final var outputEntitiesMappedByRule =
        decisionInstanceOutputEntities.stream()
            .collect(Collectors.groupingBy(e -> new RuleIdentifier(e.ruleId(), e.ruleIndex())));
    return outputEntitiesMappedByRule.entrySet().stream()
        .map(
            entry -> {
              final var ruleIdentifier = entry.getKey();
              final var outputs = entry.getValue();
              return new MatchedDecisionRuleItem()
                  .ruleId(ruleIdentifier.ruleId())
                  .ruleIndex(ruleIdentifier.ruleIndex())
                  .evaluatedOutputs(
                      outputs.stream()
                          .map(
                              output ->
                                  new EvaluatedDecisionOutputItem()
                                      .outputId(output.outputId())
                                      .outputName(output.outputName())
                                      .outputValue(output.outputValue()))
                          .toList());
            })
        .toList();
  }

  private static DecisionInstanceStateEnum toDecisionInstanceStateEnum(
      final DecisionInstanceState state) {
    if (state == null) {
      return null;
    }
    return switch (state) {
      case EVALUATED -> DecisionInstanceStateEnum.EVALUATED;
      case FAILED -> DecisionInstanceStateEnum.FAILED;
      case UNSPECIFIED -> DecisionInstanceStateEnum.UNSPECIFIED;
      default -> DecisionInstanceStateEnum.UNKNOWN;
    };
  }

  private static DecisionDefinitionTypeEnum toDecisionDefinitionTypeEnum(
      final DecisionDefinitionType decisionDefinitionType) {
    if (decisionDefinitionType == null) {
      return null;
    }
    return switch (decisionDefinitionType) {
      case DECISION_TABLE -> DecisionDefinitionTypeEnum.DECISION_TABLE;
      case LITERAL_EXPRESSION -> DecisionDefinitionTypeEnum.LITERAL_EXPRESSION;
      case UNSPECIFIED -> DecisionDefinitionTypeEnum.UNSPECIFIED;
      default -> DecisionDefinitionTypeEnum.UNKNOWN;
    };
  }

  private record RuleIdentifier(String ruleId, int ruleIndex) {}
}
