/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToOperations;
import static java.util.Optional.ofNullable;

import io.camunda.gateway.mapping.http.converters.DecisionInstanceStateConverter;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.filter.DecisionInstanceFilter;
import io.camunda.search.filter.FilterBuilders;
import java.time.OffsetDateTime;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class DecisionInstanceFilterMapper {

  private DecisionInstanceFilterMapper() {}

  public static DecisionInstanceFilter toDecisionInstanceFilter(
      final io.camunda.gateway.protocol.model.DecisionInstanceFilter filter) {
    final var builder = FilterBuilders.decisionInstance();
    ofNullable(filter.getDecisionEvaluationKey())
        .map(KeyUtil::keyToLong)
        .ifPresent(builder::decisionInstanceKeys);
    ofNullable(filter.getDecisionEvaluationInstanceKey())
        .map(mapToOperations(String.class))
        .ifPresent(builder::decisionInstanceIdOperations);
    ofNullable(filter.getState())
        .map(mapToOperations(String.class, new DecisionInstanceStateConverter()))
        .ifPresent(builder::stateOperations);
    ofNullable(filter.getEvaluationFailure()).ifPresent(builder::evaluationFailures);
    ofNullable(filter.getEvaluationDate())
        .map(mapToOperations(OffsetDateTime.class))
        .ifPresent(builder::evaluationDateOperations);
    ofNullable(filter.getProcessDefinitionKey())
        .map(KeyUtil::keyToLong)
        .ifPresent(builder::processDefinitionKeys);
    ofNullable(filter.getProcessInstanceKey())
        .map(KeyUtil::keyToLong)
        .ifPresent(builder::processInstanceKeys);
    ofNullable(filter.getElementInstanceKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::flowNodeInstanceKeyOperations);
    ofNullable(filter.getDecisionDefinitionKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::decisionDefinitionKeyOperations);
    ofNullable(filter.getDecisionDefinitionId()).ifPresent(builder::decisionDefinitionIds);
    ofNullable(filter.getDecisionDefinitionName()).ifPresent(builder::decisionDefinitionNames);
    ofNullable(filter.getDecisionDefinitionVersion())
        .ifPresent(builder::decisionDefinitionVersions);
    ofNullable(filter.getDecisionDefinitionType())
        .map(t -> Enum.valueOf(DecisionDefinitionType.class, t.name()))
        .ifPresent(builder::decisionTypes);
    ofNullable(filter.getRootDecisionDefinitionKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::rootDecisionDefinitionKeyOperations);
    ofNullable(filter.getDecisionRequirementsKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::decisionRequirementsKeyOperations);
    ofNullable(filter.getTenantId()).ifPresent(builder::tenantIds);
    return builder.build();
  }
}
