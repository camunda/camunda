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

    if (filter != null && filter.get$or().filter(l -> !l.isEmpty()).isPresent()) {
      for (final ProcessInstanceFilterFields or : filter.get$or().get()) {
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
    filter
        .getProcessInstanceKey()
        .map(mapToOperations(Long.class))
        .ifPresent(builder::processInstanceKeyOperations);
    filter
        .getProcessDefinitionId()
        .map(mapToOperations(String.class))
        .ifPresent(builder::processDefinitionIdOperations);
    filter
        .getProcessDefinitionName()
        .map(mapToOperations(String.class))
        .ifPresent(builder::processDefinitionNameOperations);
    filter
        .getProcessDefinitionVersion()
        .map(mapToOperations(Integer.class))
        .ifPresent(builder::processDefinitionVersionOperations);
    filter
        .getProcessDefinitionVersionTag()
        .map(mapToOperations(String.class))
        .ifPresent(builder::processDefinitionVersionTagOperations);
    filter
        .getProcessDefinitionKey()
        .map(mapToOperations(Long.class))
        .ifPresent(builder::processDefinitionKeyOperations);
    filter
        .getParentProcessInstanceKey()
        .map(mapToOperations(Long.class))
        .ifPresent(builder::parentProcessInstanceKeyOperations);
    filter
        .getParentElementInstanceKey()
        .map(mapToOperations(Long.class))
        .ifPresent(builder::parentFlowNodeInstanceKeyOperations);
    filter
        .getStartDate()
        .map(mapToOperations(java.time.OffsetDateTime.class))
        .ifPresent(builder::startDateOperations);
    filter
        .getEndDate()
        .map(mapToOperations(java.time.OffsetDateTime.class))
        .ifPresent(builder::endDateOperations);
    filter
        .getState()
        .map(mapToOperations(String.class, new ProcessInstanceStateConverter()))
        .ifPresent(builder::stateOperations);
    filter.getHasIncident().ifPresent(builder::hasIncident);
    filter.getTenantId().map(mapToOperations(String.class)).ifPresent(builder::tenantIdOperations);
    filter
        .getBatchOperationId()
        .map(mapToOperations(String.class))
        .ifPresent(builder::batchOperationIdOperations);
    filter
        .getErrorMessage()
        .map(mapToOperations(String.class))
        .ifPresent(builder::errorMessageOperations);
    filter.getHasRetriesLeft().ifPresent(builder::hasRetriesLeft);
    filter
        .getElementId()
        .map(mapToOperations(String.class))
        .ifPresent(builder::flowNodeIdOperations);
    filter.getHasElementInstanceIncident().ifPresent(builder::hasFlowNodeInstanceIncident);
    filter
        .getElementInstanceState()
        .map(mapToOperations(String.class))
        .ifPresent(builder::flowNodeInstanceStateOperations);
    filter
        .getIncidentErrorHashCode()
        .map(mapToOperations(Integer.class))
        .ifPresent(builder::incidentErrorHashCodeOperations);

    applyTagsAndVariables(
        filter.getTags().orElse(null),
        filter.getVariables().orElse(null),
        builder,
        validationErrors);

    filter
        .getBusinessId()
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
    filter
        .getProcessInstanceKey()
        .map(mapToKeyOperations("processInstanceKey", validationErrors))
        .ifPresent(builder::processInstanceKeyOperations);
    filter
        .getProcessDefinitionId()
        .map(mapToOperations(String.class))
        .ifPresent(builder::processDefinitionIdOperations);
    filter
        .getProcessDefinitionName()
        .map(mapToOperations(String.class))
        .ifPresent(builder::processDefinitionNameOperations);
    filter
        .getProcessDefinitionVersion()
        .map(mapToOperations(Integer.class))
        .ifPresent(builder::processDefinitionVersionOperations);
    filter
        .getProcessDefinitionVersionTag()
        .map(mapToOperations(String.class))
        .ifPresent(builder::processDefinitionVersionTagOperations);
    filter
        .getProcessDefinitionKey()
        .map(mapToKeyOperations("processDefinitionKey", validationErrors))
        .ifPresent(builder::processDefinitionKeyOperations);
    filter
        .getParentProcessInstanceKey()
        .map(mapToKeyOperations("parentProcessInstanceKey", validationErrors))
        .ifPresent(builder::parentProcessInstanceKeyOperations);
    filter
        .getParentElementInstanceKey()
        .map(mapToKeyOperations("parentElementInstanceKey", validationErrors))
        .ifPresent(builder::parentFlowNodeInstanceKeyOperations);
    filter
        .getStartDate()
        .map(mapToOffsetDateTimeOperations("startDate", validationErrors))
        .ifPresent(builder::startDateOperations);
    filter
        .getEndDate()
        .map(mapToOffsetDateTimeOperations("endDate", validationErrors))
        .ifPresent(builder::endDateOperations);
    filter
        .getState()
        .map(mapToOperations(String.class, new ProcessInstanceStateConverter()))
        .ifPresent(builder::stateOperations);
    filter.getHasIncident().ifPresent(builder::hasIncident);
    filter.getTenantId().map(mapToOperations(String.class)).ifPresent(builder::tenantIdOperations);
    filter
        .getBatchOperationId()
        .map(mapToOperations(String.class))
        .ifPresent(builder::batchOperationIdOperations);
    filter
        .getErrorMessage()
        .map(mapToOperations(String.class))
        .ifPresent(builder::errorMessageOperations);
    filter.getHasRetriesLeft().ifPresent(builder::hasRetriesLeft);
    filter
        .getElementId()
        .map(mapToOperations(String.class))
        .ifPresent(builder::flowNodeIdOperations);
    filter.getHasElementInstanceIncident().ifPresent(builder::hasFlowNodeInstanceIncident);
    filter
        .getElementInstanceState()
        .map(mapToOperations(String.class))
        .ifPresent(builder::flowNodeInstanceStateOperations);
    filter
        .getIncidentErrorHashCode()
        .map(mapToOperations(Integer.class))
        .ifPresent(builder::incidentErrorHashCodeOperations);

    applyTagsAndVariables(
        filter.getTags().orElse(null),
        filter.getVariables().orElse(null),
        builder,
        validationErrors);

    filter
        .getBusinessId()
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
