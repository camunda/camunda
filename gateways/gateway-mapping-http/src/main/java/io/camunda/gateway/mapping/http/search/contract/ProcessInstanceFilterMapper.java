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
import io.camunda.gateway.mapping.http.search.contract.generated.ProcessInstanceFilterContract;
import io.camunda.gateway.mapping.http.search.contract.generated.ProcessInstanceFilterFieldsContract;
import io.camunda.gateway.mapping.http.search.contract.generated.VariableValueFilterPropertyContract;
import io.camunda.gateway.mapping.http.validator.TagsValidator;
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
      @Nullable final ProcessInstanceFilterContract filter) {
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
      @Nullable final ProcessInstanceFilterContract filter) {
    final List<String> validationErrors = new ArrayList<>();

    final Either<List<String>, Builder> builder = toProcessInstanceFilterFields(filter);
    if (builder.isLeft()) {
      validationErrors.addAll(builder.getLeft());
    }

    if (filter != null) {
      if (filter.$or() != null && !filter.$or().isEmpty()) {
        for (final ProcessInstanceFilterFieldsContract or : filter.$or()) {
          final var orBuilder = toProcessInstanceFilterFields(or);
          if (orBuilder.isLeft()) {
            validationErrors.addAll(orBuilder.getLeft());
          } else {
            builder.get().addOrOperation(orBuilder.get().build());
          }
        }
      }
    }

    return validationErrors.isEmpty()
        ? Either.right(builder.get().build())
        : Either.left(validationErrors);
  }

  static Either<List<String>, Builder> toProcessInstanceFilterFields(
      @Nullable final ProcessInstanceFilterFieldsContract filter) {
    final var builder = FilterBuilders.processInstance();
    final List<String> validationErrors = new ArrayList<>();
    if (filter != null) {
      ofNullable(filter.processInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::processInstanceKeyOperations);
      ofNullable(filter.processDefinitionId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionIdOperations);
      ofNullable(filter.processDefinitionName())
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionNameOperations);
      ofNullable(filter.processDefinitionVersion())
          .map(mapToOperations(Integer.class))
          .ifPresent(builder::processDefinitionVersionOperations);
      ofNullable(filter.processDefinitionVersionTag())
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionVersionTagOperations);
      ofNullable(filter.processDefinitionKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::processDefinitionKeyOperations);
      ofNullable(filter.parentProcessInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::parentProcessInstanceKeyOperations);
      ofNullable(filter.parentElementInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::parentFlowNodeInstanceKeyOperations);
      ofNullable(filter.startDate())
          .map(mapToOperations(java.time.OffsetDateTime.class))
          .ifPresent(builder::startDateOperations);
      ofNullable(filter.endDate())
          .map(mapToOperations(java.time.OffsetDateTime.class))
          .ifPresent(builder::endDateOperations);
      ofNullable(filter.state())
          .map(mapToOperations(String.class, new ProcessInstanceStateConverter()))
          .ifPresent(builder::stateOperations);
      ofNullable(filter.hasIncident()).ifPresent(builder::hasIncident);
      ofNullable(filter.tenantId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::tenantIdOperations);
      ofNullable(filter.batchOperationId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::batchOperationIdOperations);
      ofNullable(filter.errorMessage())
          .map(mapToOperations(String.class))
          .ifPresent(builder::errorMessageOperations);
      ofNullable(filter.hasRetriesLeft()).ifPresent(builder::hasRetriesLeft);
      ofNullable(filter.elementId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::flowNodeIdOperations);
      ofNullable(filter.hasElementInstanceIncident())
          .ifPresent(builder::hasFlowNodeInstanceIncident);
      ofNullable(filter.elementInstanceState())
          .map(mapToOperations(String.class))
          .ifPresent(builder::flowNodeInstanceStateOperations);
      ofNullable(filter.incidentErrorHashCode())
          .map(mapToOperations(Integer.class))
          .ifPresent(builder::incidentErrorHashCodeOperations);

      applyTagsAndVariables(filter.tags(), filter.variables(), builder, validationErrors);

      ofNullable(filter.businessId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::businessIdOperations);
    }
    return validationErrors.isEmpty() ? Either.right(builder) : Either.left(validationErrors);
  }

  static Either<List<String>, Builder> toProcessInstanceFilterFields(
      @Nullable final ProcessInstanceFilterContract filter) {
    final var builder = FilterBuilders.processInstance();
    final List<String> validationErrors = new ArrayList<>();
    if (filter != null) {
      ofNullable(filter.processInstanceKey())
          .map(mapToKeyOperations("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeyOperations);
      ofNullable(filter.processDefinitionId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionIdOperations);
      ofNullable(filter.processDefinitionName())
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionNameOperations);
      ofNullable(filter.processDefinitionVersion())
          .map(mapToOperations(Integer.class))
          .ifPresent(builder::processDefinitionVersionOperations);
      ofNullable(filter.processDefinitionVersionTag())
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionVersionTagOperations);
      ofNullable(filter.processDefinitionKey())
          .map(mapToKeyOperations("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeyOperations);
      ofNullable(filter.parentProcessInstanceKey())
          .map(mapToKeyOperations("parentProcessInstanceKey", validationErrors))
          .ifPresent(builder::parentProcessInstanceKeyOperations);
      ofNullable(filter.parentElementInstanceKey())
          .map(mapToKeyOperations("parentElementInstanceKey", validationErrors))
          .ifPresent(builder::parentFlowNodeInstanceKeyOperations);
      ofNullable(filter.startDate())
          .map(mapToOffsetDateTimeOperations("startDate", validationErrors))
          .ifPresent(builder::startDateOperations);
      ofNullable(filter.endDate())
          .map(mapToOffsetDateTimeOperations("endDate", validationErrors))
          .ifPresent(builder::endDateOperations);
      ofNullable(filter.state())
          .map(mapToOperations(String.class, new ProcessInstanceStateConverter()))
          .ifPresent(builder::stateOperations);
      ofNullable(filter.hasIncident()).ifPresent(builder::hasIncident);
      ofNullable(filter.tenantId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::tenantIdOperations);
      ofNullable(filter.batchOperationId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::batchOperationIdOperations);
      ofNullable(filter.errorMessage())
          .map(mapToOperations(String.class))
          .ifPresent(builder::errorMessageOperations);
      ofNullable(filter.hasRetriesLeft()).ifPresent(builder::hasRetriesLeft);
      ofNullable(filter.elementId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::flowNodeIdOperations);
      ofNullable(filter.hasElementInstanceIncident())
          .ifPresent(builder::hasFlowNodeInstanceIncident);
      ofNullable(filter.elementInstanceState())
          .map(mapToOperations(String.class))
          .ifPresent(builder::flowNodeInstanceStateOperations);
      ofNullable(filter.incidentErrorHashCode())
          .map(mapToOperations(Integer.class))
          .ifPresent(builder::incidentErrorHashCodeOperations);

      applyTagsAndVariables(filter.tags(), filter.variables(), builder, validationErrors);

      ofNullable(filter.businessId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::businessIdOperations);
    }
    return validationErrors.isEmpty() ? Either.right(builder) : Either.left(validationErrors);
  }

  private static void applyTagsAndVariables(
      final @Nullable Set<String> tags,
      final @Nullable List<VariableValueFilterPropertyContract> variables,
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
