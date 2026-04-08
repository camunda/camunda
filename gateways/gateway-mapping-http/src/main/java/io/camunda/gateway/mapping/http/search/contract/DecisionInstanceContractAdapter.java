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

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionDefinitionTypeEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionInstanceGetQueryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionInstanceStateEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionInstanceStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedEvaluatedDecisionInputItemStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedEvaluatedDecisionOutputItemStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedMatchedDecisionRuleItemStrictContract;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceInputEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceOutputEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

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

  public static GeneratedDecisionInstanceGetQueryStrictContract toGetProjection(
      final DecisionInstanceEntity entity) {
    final var sc = toStrictContract(entity);
    return new GeneratedDecisionInstanceGetQueryStrictContract(
        sc.decisionDefinitionId(),
        sc.decisionDefinitionKey(),
        sc.decisionDefinitionName(),
        sc.decisionDefinitionType(),
        sc.decisionDefinitionVersion(),
        sc.decisionEvaluationInstanceKey(),
        sc.decisionEvaluationKey(),
        sc.elementInstanceKey(),
        sc.evaluationDate(),
        sc.evaluationFailure(),
        sc.processDefinitionKey(),
        sc.processInstanceKey(),
        sc.result(),
        sc.rootDecisionDefinitionKey(),
        sc.rootProcessInstanceKey(),
        sc.state(),
        sc.tenantId(),
        toEvaluatedInputs(entity.evaluatedInputs()),
        toMatchedRules(entity.evaluatedOutputs()));
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

  private static List<GeneratedEvaluatedDecisionInputItemStrictContract> toEvaluatedInputs(
      final List<DecisionInstanceInputEntity> decisionInstanceInputEntities) {
    if (decisionInstanceInputEntities == null) {
      return List.of();
    }
    return decisionInstanceInputEntities.stream()
        .map(
            input ->
                new GeneratedEvaluatedDecisionInputItemStrictContract(
                    input.inputId(), input.inputName(), input.inputValue()))
        .toList();
  }

  private static List<GeneratedMatchedDecisionRuleItemStrictContract> toMatchedRules(
      final List<DecisionInstanceOutputEntity> decisionInstanceOutputEntities) {
    if (decisionInstanceOutputEntities == null) {
      return List.of();
    }
    final var outputEntitiesMappedByRule =
        decisionInstanceOutputEntities.stream()
            .collect(Collectors.groupingBy(e -> new RuleIdentifier(e.ruleId(), e.ruleIndex())));
    return outputEntitiesMappedByRule.entrySet().stream()
        .map(
            entry -> {
              final var ruleIdentifier = entry.getKey();
              final var outputs = entry.getValue();
              return new GeneratedMatchedDecisionRuleItemStrictContract(
                  ruleIdentifier.ruleId(),
                  ruleIdentifier.ruleIndex(),
                  outputs.stream()
                      .map(
                          output ->
                              new GeneratedEvaluatedDecisionOutputItemStrictContract(
                                  output.outputId(),
                                  output.outputName(),
                                  output.outputValue(),
                                  null,
                                  null))
                      .toList());
            })
        .toList();
  }

  private static @Nullable GeneratedDecisionInstanceStateEnum toDecisionInstanceStateEnum(
      final DecisionInstanceState state) {
    if (state == null) {
      return null;
    }
    return switch (state) {
      case EVALUATED -> GeneratedDecisionInstanceStateEnum.EVALUATED;
      case FAILED -> GeneratedDecisionInstanceStateEnum.FAILED;
      case UNSPECIFIED -> GeneratedDecisionInstanceStateEnum.UNSPECIFIED;
      default -> GeneratedDecisionInstanceStateEnum.UNKNOWN;
    };
  }

  private static @Nullable GeneratedDecisionDefinitionTypeEnum toDecisionDefinitionTypeEnum(
      final DecisionDefinitionType decisionDefinitionType) {
    if (decisionDefinitionType == null) {
      return null;
    }
    return switch (decisionDefinitionType) {
      case DECISION_TABLE -> GeneratedDecisionDefinitionTypeEnum.DECISION_TABLE;
      case LITERAL_EXPRESSION -> GeneratedDecisionDefinitionTypeEnum.LITERAL_EXPRESSION;
      case UNSPECIFIED -> GeneratedDecisionDefinitionTypeEnum.UNSPECIFIED;
      default -> GeneratedDecisionDefinitionTypeEnum.UNKNOWN;
    };
  }

  private record RuleIdentifier(String ruleId, int ruleIndex) {}
}
