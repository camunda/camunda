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

import io.camunda.gateway.mapping.http.converters.ProcessInstanceStateConverter;
import io.camunda.gateway.protocol.model.BaseProcessInstanceFilterFields;
import io.camunda.gateway.protocol.model.VariableValueFilterProperty;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.filter.VariableValueFilter;
import io.camunda.zeebe.util.Either;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.util.CollectionUtils;

@NullMarked
public final class ProcessDefinitionStatisticsFilterMapper {

  private ProcessDefinitionStatisticsFilterMapper() {}

  public static Either<List<String>, ProcessDefinitionStatisticsFilter>
      toProcessDefinitionStatisticsFilter(
          final long processDefinitionKey,
          final io.camunda.gateway.protocol.model.ProcessDefinitionStatisticsFilter filter) {
    if (filter == null) {
      return Either.right(
          FilterBuilders.processDefinitionStatisticsFilter(processDefinitionKey).build());
    }
    final List<String> validationErrors = new ArrayList<>();
    final var builder = FilterBuilders.processDefinitionStatisticsFilter(processDefinitionKey);
    applyBaseProcessInstanceFilterFields(
        builder,
        filter.getProcessInstanceKey().orElse(null),
        filter.getParentProcessInstanceKey().orElse(null),
        filter.getParentElementInstanceKey().orElse(null),
        filter.getStartDate().orElse(null),
        filter.getEndDate().orElse(null),
        filter.getState().orElse(null),
        filter.getHasIncident().orElse(null),
        filter.getTenantId().orElse(null),
        filter.getBatchOperationId().orElse(null),
        filter.getErrorMessage().orElse(null),
        filter.getHasRetriesLeft().orElse(null),
        filter.getElementId().orElse(null),
        filter.getHasElementInstanceIncident().orElse(null),
        filter.getElementInstanceState().orElse(null),
        filter.getIncidentErrorHashCode().orElse(null),
        filter.getVariables().orElse(null),
        validationErrors);
    if (filter.get$or().filter(l -> !l.isEmpty()).isPresent()) {
      for (final BaseProcessInstanceFilterFields or : filter.get$or().get()) {
        final var orBuilder =
            FilterBuilders.processDefinitionStatisticsFilter(processDefinitionKey);
        final List<String> orErrors = new ArrayList<>();
        applyBaseProcessInstanceFilterFields(
            orBuilder,
            or.getProcessInstanceKey().orElse(null),
            or.getParentProcessInstanceKey().orElse(null),
            or.getParentElementInstanceKey().orElse(null),
            or.getStartDate().orElse(null),
            or.getEndDate().orElse(null),
            or.getState().orElse(null),
            or.getHasIncident().orElse(null),
            or.getTenantId().orElse(null),
            or.getBatchOperationId().orElse(null),
            or.getErrorMessage().orElse(null),
            or.getHasRetriesLeft().orElse(null),
            or.getElementId().orElse(null),
            or.getHasElementInstanceIncident().orElse(null),
            or.getElementInstanceState().orElse(null),
            or.getIncidentErrorHashCode().orElse(null),
            or.getVariables().orElse(null),
            orErrors);
        if (!orErrors.isEmpty()) {
          validationErrors.addAll(orErrors);
        } else {
          builder.addOrOperation(orBuilder.build());
        }
      }
    }
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  private static void applyBaseProcessInstanceFilterFields(
      final ProcessDefinitionStatisticsFilter.Builder builder,
      final @Nullable Object processInstanceKey,
      final @Nullable Object parentProcessInstanceKey,
      final @Nullable Object parentElementInstanceKey,
      final @Nullable Object startDate,
      final @Nullable Object endDate,
      final @Nullable Object state,
      final @Nullable Boolean hasIncident,
      final @Nullable Object tenantId,
      final @Nullable Object batchOperationId,
      final @Nullable Object errorMessage,
      final @Nullable Boolean hasRetriesLeft,
      final @Nullable Object elementId,
      final @Nullable Boolean hasElementInstanceIncident,
      final @Nullable Object elementInstanceState,
      final @Nullable Object incidentErrorHashCode,
      final @Nullable List<VariableValueFilterProperty> variables,
      final List<String> validationErrors) {
    ofNullable(processInstanceKey)
        .map(mapToOperations(Long.class))
        .ifPresent(builder::processInstanceKeyOperations);
    ofNullable(parentProcessInstanceKey)
        .map(mapToOperations(Long.class))
        .ifPresent(builder::parentProcessInstanceKeyOperations);
    ofNullable(parentElementInstanceKey)
        .map(mapToOperations(Long.class))
        .ifPresent(builder::parentFlowNodeInstanceKeyOperations);
    ofNullable(startDate)
        .map(mapToOperations(OffsetDateTime.class))
        .ifPresent(builder::startDateOperations);
    ofNullable(endDate)
        .map(mapToOperations(OffsetDateTime.class))
        .ifPresent(builder::endDateOperations);
    ofNullable(state)
        .map(mapToOperations(String.class, new ProcessInstanceStateConverter()))
        .ifPresent(builder::stateOperations);
    ofNullable(hasIncident).ifPresent(builder::hasIncident);
    ofNullable(tenantId).map(mapToOperations(String.class)).ifPresent(builder::tenantIdOperations);
    ofNullable(batchOperationId)
        .map(mapToOperations(String.class))
        .ifPresent(builder::batchOperationIdOperations);
    ofNullable(errorMessage)
        .map(mapToOperations(String.class))
        .ifPresent(builder::errorMessageOperations);
    ofNullable(hasRetriesLeft).ifPresent(builder::hasRetriesLeft);
    ofNullable(elementId)
        .map(mapToOperations(String.class))
        .ifPresent(builder::flowNodeIdOperations);
    ofNullable(hasElementInstanceIncident).ifPresent(builder::hasFlowNodeInstanceIncident);
    ofNullable(elementInstanceState)
        .map(mapToOperations(String.class))
        .ifPresent(builder::flowNodeInstanceStateOperations);
    ofNullable(incidentErrorHashCode)
        .map(mapToOperations(Integer.class))
        .ifPresent(builder::incidentErrorHashCodeOperations);
    if (!CollectionUtils.isEmpty(variables)) {
      final Either<List<String>, List<VariableValueFilter>> either =
          VariableValueFilterUtil.toStrictVariableValueFilters(variables);
      if (either.isLeft()) {
        validationErrors.addAll(either.getLeft());
      } else {
        builder.variables(either.get());
      }
    }
  }
}
