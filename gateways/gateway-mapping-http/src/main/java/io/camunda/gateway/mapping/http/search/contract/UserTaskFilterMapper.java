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

import io.camunda.gateway.mapping.http.validator.TagsValidator;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.UserTaskFilter;
import io.camunda.search.filter.VariableValueFilter;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.springframework.util.CollectionUtils;

@NullMarked
public final class UserTaskFilterMapper {

  private UserTaskFilterMapper() {}

  public static Either<List<String>, UserTaskFilter> toUserTaskFilter(
      final io.camunda.gateway.protocol.model.UserTaskFilter filter) {
    final var builder = FilterBuilders.userTask();
    final List<String> validationErrors = new ArrayList<>();
    if (filter != null) {
      Optional.ofNullable(filter.getUserTaskKey())
          .map(mapKeyToLong("userTaskKey", validationErrors))
          .ifPresent(builder::userTaskKeys);
      Optional.ofNullable(filter.getState())
          .map(mapToOperations(String.class))
          .ifPresent(builder::stateOperations);
      Optional.ofNullable(filter.getProcessDefinitionId()).ifPresent(builder::bpmnProcessIds);
      Optional.ofNullable(filter.getElementId()).ifPresent(builder::elementIds);
      Optional.ofNullable(filter.getName())
          .map(mapToOperations(String.class))
          .ifPresent(builder::nameOperations);
      Optional.ofNullable(filter.getAssignee())
          .map(mapToOperations(String.class))
          .ifPresent(builder::assigneeOperations);
      Optional.ofNullable(filter.getPriority())
          .map(mapToOperations(Integer.class))
          .ifPresent(builder::priorityOperations);
      Optional.ofNullable(filter.getCandidateGroup())
          .map(mapToOperations(String.class))
          .ifPresent(builder::candidateGroupOperations);
      Optional.ofNullable(filter.getCandidateUser())
          .map(mapToOperations(String.class))
          .ifPresent(builder::candidateUserOperations);
      Optional.ofNullable(filter.getProcessDefinitionKey())
          .map(mapKeyToLong("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeys);
      Optional.ofNullable(filter.getProcessInstanceKey())
          .map(mapKeyToLong("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeys);
      Optional.ofNullable(filter.getTenantId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::tenantIdOperations);
      Optional.ofNullable(filter.getElementInstanceKey())
          .map(mapKeyToLong("elementInstanceKey", validationErrors))
          .ifPresent(builder::elementInstanceKeys);
      if (!CollectionUtils.isEmpty(filter.getProcessInstanceVariables())) {
        final Either<List<String>, List<VariableValueFilter>> either =
            VariableValueFilterUtil.toStrictVariableValueFilters(
                filter.getProcessInstanceVariables());
        if (either.isLeft()) {
          validationErrors.addAll(either.getLeft());
        } else {
          builder.processInstanceVariables(either.get());
        }
      }
      if (!CollectionUtils.isEmpty(filter.getLocalVariables())) {
        final Either<List<String>, List<VariableValueFilter>> either =
            VariableValueFilterUtil.toStrictVariableValueFilters(filter.getLocalVariables());
        if (either.isLeft()) {
          validationErrors.addAll(either.getLeft());
        } else {
          builder.localVariables(either.get());
        }
      }
      Optional.ofNullable(filter.getCreationDate())
          .map(mapToOffsetDateTimeOperations("creationDate", validationErrors))
          .ifPresent(builder::creationDateOperations);
      Optional.ofNullable(filter.getCompletionDate())
          .map(mapToOffsetDateTimeOperations("completionDate", validationErrors))
          .ifPresent(builder::completionDateOperations);
      Optional.ofNullable(filter.getDueDate())
          .map(mapToOffsetDateTimeOperations("dueDate", validationErrors))
          .ifPresent(builder::dueDateOperations);
      Optional.ofNullable(filter.getFollowUpDate())
          .map(mapToOffsetDateTimeOperations("followUpDate", validationErrors))
          .ifPresent(builder::followUpDateOperations);
      if (!CollectionUtils.isEmpty(filter.getTags())) {
        final var tagErrors = TagsValidator.validate(filter.getTags());
        if (tagErrors.isEmpty()) {
          ofNullable(filter.getTags()).ifPresent(builder::tags);
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
