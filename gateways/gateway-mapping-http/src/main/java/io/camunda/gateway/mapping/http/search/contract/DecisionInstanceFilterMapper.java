/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToOperations;

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
    filter
        .getDecisionEvaluationKey()
        .map(KeyUtil::keyToLong)
        .ifPresent(builder::decisionInstanceKeys);
    filter
        .getDecisionEvaluationInstanceKey()
        .map(mapToOperations(String.class))
        .ifPresent(builder::decisionInstanceIdOperations);
    filter
        .getState()
        .map(mapToOperations(String.class, new DecisionInstanceStateConverter()))
        .ifPresent(builder::stateOperations);
    filter.getEvaluationFailure().ifPresent(builder::evaluationFailures);
    filter
        .getEvaluationDate()
        .map(mapToOperations(OffsetDateTime.class))
        .ifPresent(builder::evaluationDateOperations);
    filter
        .getProcessDefinitionKey()
        .map(KeyUtil::keyToLong)
        .ifPresent(builder::processDefinitionKeys);
    filter.getProcessInstanceKey().map(KeyUtil::keyToLong).ifPresent(builder::processInstanceKeys);
    filter
        .getElementInstanceKey()
        .map(mapToOperations(Long.class))
        .ifPresent(builder::flowNodeInstanceKeyOperations);
    filter
        .getDecisionDefinitionKey()
        .map(mapToOperations(Long.class))
        .ifPresent(builder::decisionDefinitionKeyOperations);
    filter.getDecisionDefinitionId().ifPresent(builder::decisionDefinitionIds);
    filter.getDecisionDefinitionName().ifPresent(builder::decisionDefinitionNames);
    filter.getDecisionDefinitionVersion().ifPresent(builder::decisionDefinitionVersions);
    filter
        .getDecisionDefinitionType()
        .map(t -> Enum.valueOf(DecisionDefinitionType.class, t.name()))
        .ifPresent(builder::decisionTypes);
    filter
        .getRootDecisionDefinitionKey()
        .map(mapToOperations(Long.class))
        .ifPresent(builder::rootDecisionDefinitionKeyOperations);
    filter
        .getDecisionRequirementsKey()
        .map(mapToOperations(Long.class))
        .ifPresent(builder::decisionRequirementsKeyOperations);
    filter.getTenantId().ifPresent(builder::tenantIds);
    return builder.build();
  }
}
