/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import static java.util.Optional.ofNullable;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceInputEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceOutputEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState;
import io.camunda.webapps.schema.entities.dmn.DecisionType;
import java.util.List;

public class DecisionInstanceEntityTransformer
    implements ServiceTransformer<
        io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity, DecisionInstanceEntity> {

  @Override
  public DecisionInstanceEntity apply(
      final io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity source) {
    return new DecisionInstanceEntity(
        source.getId(),
        source.getKey(),
        toDecisionInstanceState(source.getState()),
        source.getEvaluationDate(),
        source.getEvaluationFailure(),
        source.getProcessDefinitionKey(),
        source.getProcessInstanceKey(),
        source.getTenantId(),
        source.getDecisionId(),
        ofNullable(source.getDecisionDefinitionId()).map(Long::valueOf).orElse(null),
        source.getDecisionName(),
        source.getDecisionVersion(),
        toDecisionType(source.getDecisionType()),
        source.getResult(),
        toEvaluatedInputs(source.getEvaluatedInputs()),
        toEvaluatedOutputs(source.getEvaluatedOutputs()));
  }

  private List<DecisionInstanceOutputEntity> toEvaluatedOutputs(
      final List<io.camunda.webapps.schema.entities.dmn.DecisionInstanceOutputEntity> source) {
    if (source == null) {
      return null;
    }
    return source.stream()
        .map(
            s ->
                new DecisionInstanceOutputEntity(
                    s.getId(), s.getName(), s.getValue(), s.getRuleId(), s.getRuleIndex()))
        .toList();
  }

  private List<DecisionInstanceInputEntity> toEvaluatedInputs(
      final List<io.camunda.webapps.schema.entities.dmn.DecisionInstanceInputEntity> source) {
    if (source == null) {
      return null;
    }
    return source.stream()
        .map(s -> new DecisionInstanceInputEntity(s.getId(), s.getName(), s.getValue()))
        .toList();
  }

  private DecisionDefinitionType toDecisionType(final DecisionType source) {
    if (source == null) {
      return null;
    }
    return switch (source) {
      case DECISION_TABLE -> DecisionDefinitionType.DECISION_TABLE;
      case LITERAL_EXPRESSION -> DecisionDefinitionType.LITERAL_EXPRESSION;
      case UNSPECIFIED -> DecisionDefinitionType.UNSPECIFIED;
      default -> DecisionDefinitionType.UNKNOWN;
    };
  }

  private DecisionInstanceState toDecisionInstanceState(
      final io.camunda.webapps.schema.entities.dmn.DecisionInstanceState source) {
    if (source == null) {
      return null;
    }
    return switch (source) {
      case EVALUATED -> DecisionInstanceState.EVALUATED;
      case FAILED -> DecisionInstanceState.FAILED;
      default -> DecisionInstanceState.UNKNOWN;
    };
  }
}
