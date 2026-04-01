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
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionInstanceFilterStrictContract;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.filter.DecisionInstanceFilter;
import io.camunda.search.filter.FilterBuilders;
import java.time.OffsetDateTime;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class DecisionInstanceFilterMapper {

  private DecisionInstanceFilterMapper() {}

  public static DecisionInstanceFilter toDecisionInstanceFilter(
      @Nullable final GeneratedDecisionInstanceFilterStrictContract filter) {
    final var builder = FilterBuilders.decisionInstance();
    if (filter != null) {
      ofNullable(filter.decisionEvaluationKey())
          .map(KeyUtil::keyToLong)
          .ifPresent(builder::decisionInstanceKeys);
      ofNullable(filter.decisionEvaluationInstanceKey())
          .map(mapToOperations(String.class))
          .ifPresent(builder::decisionInstanceIdOperations);
      ofNullable(filter.state())
          .map(mapToOperations(String.class, new DecisionInstanceStateConverter()))
          .ifPresent(builder::stateOperations);
      ofNullable(filter.evaluationFailure()).ifPresent(builder::evaluationFailures);
      ofNullable(filter.evaluationDate())
          .map(mapToOperations(OffsetDateTime.class))
          .ifPresent(builder::evaluationDateOperations);
      ofNullable(filter.processDefinitionKey())
          .map(KeyUtil::keyToLong)
          .ifPresent(builder::processDefinitionKeys);
      ofNullable(filter.processInstanceKey())
          .map(KeyUtil::keyToLong)
          .ifPresent(builder::processInstanceKeys);
      ofNullable(filter.elementInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::flowNodeInstanceKeyOperations);
      ofNullable(filter.decisionDefinitionKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::decisionDefinitionKeyOperations);
      ofNullable(filter.decisionDefinitionId()).ifPresent(builder::decisionDefinitionIds);
      ofNullable(filter.decisionDefinitionName()).ifPresent(builder::decisionDefinitionNames);
      ofNullable(filter.decisionDefinitionVersion()).ifPresent(builder::decisionDefinitionVersions);
      ofNullable(filter.decisionDefinitionType())
          .map(t -> Enum.valueOf(DecisionDefinitionType.class, t.name()))
          .ifPresent(builder::decisionTypes);
      ofNullable(filter.rootDecisionDefinitionKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::rootDecisionDefinitionKeyOperations);
      ofNullable(filter.decisionRequirementsKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::decisionRequirementsKeyOperations);
      ofNullable(filter.tenantId()).ifPresent(builder::tenantIds);
    }
    return builder.build();
  }
}
