/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToKeyOperations;
import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToOffsetDateTimeOperations;
import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToOperations;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_AT_LEAST_ONE_FIELD;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static java.util.Optional.ofNullable;

import io.camunda.gateway.mapping.http.converters.ProcessInstanceStateConverter;
import io.camunda.gateway.mapping.http.validator.TagsValidator;
import io.camunda.gateway.protocol.model.ProcessInstanceFilterFields;
import io.camunda.gateway.protocol.model.VariableValueFilterProperty;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.filter.ProcessInstanceFilter.Builder;
import io.camunda.search.filter.VariableValueFilter;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.util.CollectionUtils;

@NullMarked
public final class ProcessInstanceFilterMapper {

  private ProcessInstanceFilterMapper() {}

  public static Either<List<String>, ProcessInstanceFilter> toRequiredProcessInstanceFilter(
      final io.camunda.gateway.protocol.model.ProcessInstanceFilter filter) {
    if (filter == null) {
      return Either.left(List.of(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("filter")));
    }
    final var result = toProcessInstanceFilter(filter);
    if (result.isLeft()) {
      return result;
    }
    if (result.get().equals(FilterBuilders.processInstance().build())) {
      return Either.left(List.of(ERROR_MESSAGE_AT_LEAST_ONE_FIELD.formatted("filter criteria")));
    }
    return result;
  }

  public static Either<List<String>, ProcessInstanceFilter> toProcessInstanceFilter(
      final io.camunda.gateway.protocol.model.ProcessInstanceFilter filter) {
    final List<String> validationErrors = new ArrayList<>();

    final Either<List<String>, Builder> builder = toProcessInstanceFilterFields(filter);
    if (builder.isLeft()) {
      validationErrors.addAll(builder.getLeft());
    }

    if (filter != null && filter.get$or() != null && !filter.get$or().isEmpty()) {
      for (final ProcessInstanceFilterFields or : filter.get$or()) {
        final var orBuilder = toProcessInstanceFilterFields(or);
        if (orBuilder.isLeft()) {
          validationErrors.addAll(orBuilder.getLeft());
        } else {
          builder.get().addOrOperation(orBuilder.get().build());
        }
      }
    }

    return validationErrors.isEmpty()
        ? Either.right(builder.get().build())
        : Either.left(validationErrors);
  }

  static Either<List<String>, Builder> toProcessInstanceFilterFields(
      @Nullable final ProcessInstanceFilterFields filter) {
    if (filter == null) {
      return Either.right(FilterBuilders.processInstance());
    }
    final var builder = FilterBuilders.processInstance();
    final List<String> validationErrors = new ArrayList<>();
    ofNullable(filter.getProcessInstanceKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::processInstanceKeyOperations);
    ofNullable(filter.getProcessDefinitionId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::processDefinitionIdOperations);
    ofNullable(filter.getProcessDefinitionName())
        .map(mapToOperations(String.class))
        .ifPresent(builder::processDefinitionNameOperations);
    ofNullable(filter.getProcessDefinitionVersion())
        .map(mapToOperations(Integer.class))
        .ifPresent(builder::processDefinitionVersionOperations);
    ofNullable(filter.getProcessDefinitionVersionTag())
        .map(mapToOperations(String.class))
        .ifPresent(builder::processDefinitionVersionTagOperations);
    ofNullable(filter.getProcessDefinitionKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::processDefinitionKeyOperations);
    ofNullable(filter.getParentProcessInstanceKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::parentProcessInstanceKeyOperations);
    ofNullable(filter.getParentElementInstanceKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::parentFlowNodeInstanceKeyOperations);
    ofNullable(filter.getStartDate())
        .map(mapToOperations(java.time.OffsetDateTime.class))
        .ifPresent(builder::startDateOperations);
    ofNullable(filter.getEndDate())
        .map(mapToOperations(java.time.OffsetDateTime.class))
        .ifPresent(builder::endDateOperations);
    ofNullable(filter.getState())
        .map(mapToOperations(String.class, new ProcessInstanceStateConverter()))
        .ifPresent(builder::stateOperations);
    ofNullable(filter.getHasIncident()).ifPresent(builder::hasIncident);
    ofNullable(filter.getTenantId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::tenantIdOperations);
    ofNullable(filter.getBatchOperationId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::batchOperationIdOperations);
    ofNullable(filter.getErrorMessage())
        .map(mapToOperations(String.class))
        .ifPresent(builder::errorMessageOperations);
    ofNullable(filter.getHasRetriesLeft()).ifPresent(builder::hasRetriesLeft);
    ofNullable(filter.getElementId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::flowNodeIdOperations);
    ofNullable(filter.getHasElementInstanceIncident())
        .ifPresent(builder::hasFlowNodeInstanceIncident);
    ofNullable(filter.getElementInstanceState())
        .map(mapToOperations(String.class))
        .ifPresent(builder::flowNodeInstanceStateOperations);
    ofNullable(filter.getIncidentErrorHashCode())
        .map(mapToOperations(Integer.class))
        .ifPresent(builder::incidentErrorHashCodeOperations);

    applyTagsAndVariables(filter.getTags(), filter.getVariables(), builder, validationErrors);

    ofNullable(filter.getBusinessId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::businessIdOperations);
    return validationErrors.isEmpty() ? Either.right(builder) : Either.left(validationErrors);
  }

  static Either<List<String>, Builder> toProcessInstanceFilterFields(
      final io.camunda.gateway.protocol.model.ProcessInstanceFilter filter) {
    if (filter == null) {
      return Either.right(FilterBuilders.processInstance());
    }
    final var builder = FilterBuilders.processInstance();
    final List<String> validationErrors = new ArrayList<>();
    ofNullable(filter.getProcessInstanceKey())
        .map(mapToKeyOperations("processInstanceKey", validationErrors))
        .ifPresent(builder::processInstanceKeyOperations);
    ofNullable(filter.getProcessDefinitionId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::processDefinitionIdOperations);
    ofNullable(filter.getProcessDefinitionName())
        .map(mapToOperations(String.class))
        .ifPresent(builder::processDefinitionNameOperations);
    ofNullable(filter.getProcessDefinitionVersion())
        .map(mapToOperations(Integer.class))
        .ifPresent(builder::processDefinitionVersionOperations);
    ofNullable(filter.getProcessDefinitionVersionTag())
        .map(mapToOperations(String.class))
        .ifPresent(builder::processDefinitionVersionTagOperations);
    ofNullable(filter.getProcessDefinitionKey())
        .map(mapToKeyOperations("processDefinitionKey", validationErrors))
        .ifPresent(builder::processDefinitionKeyOperations);
    ofNullable(filter.getParentProcessInstanceKey())
        .map(mapToKeyOperations("parentProcessInstanceKey", validationErrors))
        .ifPresent(builder::parentProcessInstanceKeyOperations);
    ofNullable(filter.getParentElementInstanceKey())
        .map(mapToKeyOperations("parentElementInstanceKey", validationErrors))
        .ifPresent(builder::parentFlowNodeInstanceKeyOperations);
    ofNullable(filter.getStartDate())
        .map(mapToOffsetDateTimeOperations("startDate", validationErrors))
        .ifPresent(builder::startDateOperations);
    ofNullable(filter.getEndDate())
        .map(mapToOffsetDateTimeOperations("endDate", validationErrors))
        .ifPresent(builder::endDateOperations);
    ofNullable(filter.getState())
        .map(mapToOperations(String.class, new ProcessInstanceStateConverter()))
        .ifPresent(builder::stateOperations);
    ofNullable(filter.getHasIncident()).ifPresent(builder::hasIncident);
    ofNullable(filter.getTenantId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::tenantIdOperations);
    ofNullable(filter.getBatchOperationId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::batchOperationIdOperations);
    ofNullable(filter.getErrorMessage())
        .map(mapToOperations(String.class))
        .ifPresent(builder::errorMessageOperations);
    ofNullable(filter.getHasRetriesLeft()).ifPresent(builder::hasRetriesLeft);
    ofNullable(filter.getElementId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::flowNodeIdOperations);
    ofNullable(filter.getHasElementInstanceIncident())
        .ifPresent(builder::hasFlowNodeInstanceIncident);
    ofNullable(filter.getElementInstanceState())
        .map(mapToOperations(String.class))
        .ifPresent(builder::flowNodeInstanceStateOperations);
    ofNullable(filter.getIncidentErrorHashCode())
        .map(mapToOperations(Integer.class))
        .ifPresent(builder::incidentErrorHashCodeOperations);

    applyTagsAndVariables(filter.getTags(), filter.getVariables(), builder, validationErrors);

    ofNullable(filter.getBusinessId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::businessIdOperations);
    return validationErrors.isEmpty() ? Either.right(builder) : Either.left(validationErrors);
  }

  private static void applyTagsAndVariables(
      final @Nullable Set<String> tags,
      final @Nullable List<VariableValueFilterProperty> variables,
      final Builder builder,
      final List<String> validationErrors) {
    if (!CollectionUtils.isEmpty(tags)) {
      final var tagErrors = TagsValidator.validate(tags);
      if (tagErrors.isEmpty()) {
        ofNullable(tags).ifPresent(builder::tags);
      } else {
        validationErrors.addAll(tagErrors);
      }
    }
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
