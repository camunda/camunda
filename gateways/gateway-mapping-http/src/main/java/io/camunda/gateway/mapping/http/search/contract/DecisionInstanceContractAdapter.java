/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.ResponseMapper.formatDate;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import io.camunda.gateway.protocol.model.DecisionDefinitionTypeEnum;
import io.camunda.gateway.protocol.model.DecisionInstanceGetQueryResult;
import io.camunda.gateway.protocol.model.DecisionInstanceResult;
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
import org.jspecify.annotations.Nullable;

/**
 * Contract adaptation layer for decision instance response projections.
 *
 * <p>Policy in this adapter decides which projection to emit by operation context: search emits the
 * base projection while get enriches with evaluated inputs and matched rules.
 */
public final class DecisionInstanceContractAdapter {

  private DecisionInstanceContractAdapter() {}

  public static List<DecisionInstanceResult> toSearchProjections(
      final List<DecisionInstanceEntity> instances) {
    return instances.stream().map(DecisionInstanceContractAdapter::toSearchProjection).toList();
  }

  public static DecisionInstanceResult toSearchProjection(final DecisionInstanceEntity entity) {
    return toContract(entity);
  }

  public static DecisionInstanceGetQueryResult toGetProjection(
      final DecisionInstanceEntity entity) {
    final var sc = toContract(entity);
    return new DecisionInstanceGetQueryResult()
        .decisionDefinitionId(sc.getDecisionDefinitionId())
        .decisionDefinitionKey(sc.getDecisionDefinitionKey())
        .decisionDefinitionName(sc.getDecisionDefinitionName())
        .decisionDefinitionType(sc.getDecisionDefinitionType())
        .decisionDefinitionVersion(sc.getDecisionDefinitionVersion())
        .decisionEvaluationInstanceKey(sc.getDecisionEvaluationInstanceKey())
        .decisionEvaluationKey(sc.getDecisionEvaluationKey())
        .elementInstanceKey(sc.getElementInstanceKey())
        .evaluationDate(sc.getEvaluationDate())
        .evaluationFailure(sc.getEvaluationFailure())
        .processDefinitionKey(sc.getProcessDefinitionKey())
        .processInstanceKey(sc.getProcessInstanceKey())
        .result(sc.getResult())
        .rootDecisionDefinitionKey(sc.getRootDecisionDefinitionKey())
        .rootProcessInstanceKey(sc.getRootProcessInstanceKey())
        .state(sc.getState())
        .tenantId(sc.getTenantId())
        .evaluatedInputs(toEvaluatedInputs(entity.evaluatedInputs()))
        .matchedRules(toMatchedRules(entity.evaluatedOutputs()));
  }

  private static DecisionInstanceResult toContract(final DecisionInstanceEntity entity) {
    return new DecisionInstanceResult()
        .decisionDefinitionId(
            ContractPolicy.requireNonNull(
                entity.decisionDefinitionId(), "decisionDefinitionId", entity))
        .decisionDefinitionKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.decisionDefinitionKey()),
                "decisionDefinitionKey",
                entity))
        .decisionDefinitionName(
            ContractPolicy.requireNonNull(
                entity.decisionDefinitionName(), "decisionDefinitionName", entity))
        .decisionDefinitionType(
            ContractPolicy.requireNonNull(
                toDecisionDefinitionTypeEnum(entity.decisionDefinitionType()),
                "decisionDefinitionType",
                entity))
        .decisionDefinitionVersion(
            ContractPolicy.requireNonNull(
                entity.decisionDefinitionVersion(), "decisionDefinitionVersion", entity))
        .decisionEvaluationInstanceKey(
            ContractPolicy.requireNonNull(
                entity.decisionInstanceId(), "decisionEvaluationInstanceKey", entity))
        .decisionEvaluationKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.decisionInstanceKey()), "decisionEvaluationKey", entity))
        .evaluationDate(
            ContractPolicy.requireNonNull(
                formatDate(entity.evaluationDate()), "evaluationDate", entity))
        .result(ContractPolicy.requireNonNull(entity.result(), "result", entity))
        .rootDecisionDefinitionKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.rootDecisionDefinitionKey()),
                "rootDecisionDefinitionKey",
                entity))
        .state(
            ContractPolicy.requireNonNull(
                toDecisionInstanceStateEnum(entity.state()), "state", entity))
        .tenantId(ContractPolicy.requireNonNull(entity.tenantId(), "tenantId", entity))
        .elementInstanceKey(KeyUtil.keyToString(entity.flowNodeInstanceKey()))
        .evaluationFailure(entity.evaluationFailure())
        .processDefinitionKey(KeyUtil.keyToString(entity.processDefinitionKey()))
        .processInstanceKey(KeyUtil.keyToString(entity.processInstanceKey()))
        .rootProcessInstanceKey(KeyUtil.keyToString(entity.rootProcessInstanceKey()));
  }

  private static List<EvaluatedDecisionInputItem> toEvaluatedInputs(
      final List<DecisionInstanceInputEntity> decisionInstanceInputEntities) {
    if (decisionInstanceInputEntities == null) {
      return List.of();
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

  private static @Nullable DecisionInstanceStateEnum toDecisionInstanceStateEnum(
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

  private static @Nullable DecisionDefinitionTypeEnum toDecisionDefinitionTypeEnum(
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
