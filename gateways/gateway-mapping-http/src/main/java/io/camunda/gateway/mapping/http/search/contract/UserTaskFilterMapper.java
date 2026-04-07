/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToOffsetDateTimeOperations;
import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToOperations;
import static io.camunda.gateway.mapping.http.util.KeyUtil.mapKeyToLong;
import static java.util.Optional.ofNullable;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskFilterStrictContract;
import io.camunda.gateway.mapping.http.validator.TagsValidator;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.UserTaskFilter;
import io.camunda.search.filter.VariableValueFilter;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.util.CollectionUtils;

@NullMarked
public final class UserTaskFilterMapper {

  private UserTaskFilterMapper() {}

  public static Either<List<String>, UserTaskFilter> toUserTaskFilter(
      @Nullable final GeneratedUserTaskFilterStrictContract filter) {
    final var builder = FilterBuilders.userTask();
    final List<String> validationErrors = new ArrayList<>();
    if (filter != null) {
      Optional.ofNullable(filter.userTaskKey())
          .map(mapKeyToLong("userTaskKey", validationErrors))
          .ifPresent(builder::userTaskKeys);
      Optional.ofNullable(filter.state())
          .map(mapToOperations(String.class))
          .ifPresent(builder::stateOperations);
      Optional.ofNullable(filter.processDefinitionId()).ifPresent(builder::bpmnProcessIds);
      Optional.ofNullable(filter.elementId()).ifPresent(builder::elementIds);
      Optional.ofNullable(filter.name())
          .map(mapToOperations(String.class))
          .ifPresent(builder::nameOperations);
      Optional.ofNullable(filter.assignee())
          .map(mapToOperations(String.class))
          .ifPresent(builder::assigneeOperations);
      Optional.ofNullable(filter.priority())
          .map(mapToOperations(Integer.class))
          .ifPresent(builder::priorityOperations);
      Optional.ofNullable(filter.candidateGroup())
          .map(mapToOperations(String.class))
          .ifPresent(builder::candidateGroupOperations);
      Optional.ofNullable(filter.candidateUser())
          .map(mapToOperations(String.class))
          .ifPresent(builder::candidateUserOperations);
      Optional.ofNullable(filter.processDefinitionKey())
          .map(mapKeyToLong("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeys);
      Optional.ofNullable(filter.processInstanceKey())
          .map(mapKeyToLong("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeys);
      Optional.ofNullable(filter.tenantId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::tenantIdOperations);
      Optional.ofNullable(filter.elementInstanceKey())
          .map(mapKeyToLong("elementInstanceKey", validationErrors))
          .ifPresent(builder::elementInstanceKeys);
      if (!CollectionUtils.isEmpty(filter.processInstanceVariables())) {
        final Either<List<String>, List<VariableValueFilter>> either =
            VariableValueFilterUtil.toStrictVariableValueFilters(filter.processInstanceVariables());
        if (either.isLeft()) {
          validationErrors.addAll(either.getLeft());
        } else {
          builder.processInstanceVariables(either.get());
        }
      }
      if (!CollectionUtils.isEmpty(filter.localVariables())) {
        final Either<List<String>, List<VariableValueFilter>> either =
            VariableValueFilterUtil.toStrictVariableValueFilters(filter.localVariables());
        if (either.isLeft()) {
          validationErrors.addAll(either.getLeft());
        } else {
          builder.localVariables(either.get());
        }
      }
      Optional.ofNullable(filter.creationDate())
          .map(mapToOffsetDateTimeOperations("creationDate", validationErrors))
          .ifPresent(builder::creationDateOperations);
      Optional.ofNullable(filter.completionDate())
          .map(mapToOffsetDateTimeOperations("completionDate", validationErrors))
          .ifPresent(builder::completionDateOperations);
      Optional.ofNullable(filter.dueDate())
          .map(mapToOffsetDateTimeOperations("dueDate", validationErrors))
          .ifPresent(builder::dueDateOperations);
      Optional.ofNullable(filter.followUpDate())
          .map(mapToOffsetDateTimeOperations("followUpDate", validationErrors))
          .ifPresent(builder::followUpDateOperations);
      if (!CollectionUtils.isEmpty(filter.tags())) {
        final var tagErrors = TagsValidator.validate(filter.tags());
        if (tagErrors.isEmpty()) {
          ofNullable(filter.tags()).ifPresent(builder::tags);
        } else {
          validationErrors.addAll(tagErrors);
        }
      }
    }
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }
}
