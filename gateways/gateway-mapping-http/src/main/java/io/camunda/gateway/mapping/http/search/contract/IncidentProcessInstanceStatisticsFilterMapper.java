/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedIncidentProcessInstanceStatisticsByDefinitionFilterStrictContract;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.IncidentProcessInstanceStatisticsByDefinitionFilter;
import io.camunda.zeebe.util.Either;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class IncidentProcessInstanceStatisticsFilterMapper {

  private IncidentProcessInstanceStatisticsFilterMapper() {}

  public static Either<List<String>, IncidentProcessInstanceStatisticsByDefinitionFilter>
      toIncidentProcessInstanceStatisticsByDefinitionFilter(
          final GeneratedIncidentProcessInstanceStatisticsByDefinitionFilterStrictContract filter) {
    if (filter == null) {
      return Either.left(List.of(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("filter")));
    }
    return Either.right(
        FilterBuilders.incidentProcessInstanceStatisticsByDefinition(
            f -> f.state(IncidentState.ACTIVE.name()).errorHashCode(filter.errorHashCode())));
  }
}
