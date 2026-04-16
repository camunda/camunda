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

import io.camunda.gateway.mapping.http.validator.TagsValidator;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.UserTaskFilter;
import io.camunda.search.filter.VariableValueFilter;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.springframework.util.CollectionUtils;

@NullMarked
public final class UserTaskFilterMapper {

  private UserTaskFilterMapper() {}

  public static Either<List<String>, UserTaskFilter> toUserTaskFilter(
      final io.camunda.gateway.protocol.model.UserTaskFilter filter) {
    final var builder = FilterBuilders.userTask();
    final List<String> validationErrors = new ArrayList<>();
    filter
        .getUserTaskKey()
        .map(mapKeyToLong("userTaskKey", validationErrors))
        .ifPresent(builder::userTaskKeys);
    filter.getState().map(mapToOperations(String.class)).ifPresent(builder::stateOperations);
    filter.getProcessDefinitionId().ifPresent(builder::bpmnProcessIds);
    filter.getElementId().ifPresent(builder::elementIds);
    filter.getName().map(mapToOperations(String.class)).ifPresent(builder::nameOperations);
    filter.getAssignee().map(mapToOperations(String.class)).ifPresent(builder::assigneeOperations);
    filter.getPriority().map(mapToOperations(Integer.class)).ifPresent(builder::priorityOperations);
    filter
        .getCandidateGroup()
        .map(mapToOperations(String.class))
        .ifPresent(builder::candidateGroupOperations);
    filter
        .getCandidateUser()
        .map(mapToOperations(String.class))
        .ifPresent(builder::candidateUserOperations);
    filter
        .getProcessDefinitionKey()
        .map(mapKeyToLong("processDefinitionKey", validationErrors))
        .ifPresent(builder::processDefinitionKeys);
    filter
        .getProcessInstanceKey()
        .map(mapKeyToLong("processInstanceKey", validationErrors))
        .ifPresent(builder::processInstanceKeys);
    filter.getTenantId().map(mapToOperations(String.class)).ifPresent(builder::tenantIdOperations);
    filter
        .getElementInstanceKey()
        .map(mapKeyToLong("elementInstanceKey", validationErrors))
        .ifPresent(builder::elementInstanceKeys);
    if (!CollectionUtils.isEmpty(filter.getProcessInstanceVariables().orElse(null))) {
      final Either<List<String>, List<VariableValueFilter>> either =
          VariableValueFilterUtil.toStrictVariableValueFilters(
              filter.getProcessInstanceVariables().orElse(null));
      if (either.isLeft()) {
        validationErrors.addAll(either.getLeft());
      } else {
        builder.processInstanceVariables(either.get());
      }
    }
    if (!CollectionUtils.isEmpty(filter.getLocalVariables().orElse(null))) {
      final Either<List<String>, List<VariableValueFilter>> either =
          VariableValueFilterUtil.toStrictVariableValueFilters(
              filter.getLocalVariables().orElse(null));
      if (either.isLeft()) {
        validationErrors.addAll(either.getLeft());
      } else {
        builder.localVariables(either.get());
      }
    }
    filter
        .getCreationDate()
        .map(mapToOffsetDateTimeOperations("creationDate", validationErrors))
        .ifPresent(builder::creationDateOperations);
    filter
        .getCompletionDate()
        .map(mapToOffsetDateTimeOperations("completionDate", validationErrors))
        .ifPresent(builder::completionDateOperations);
    filter
        .getDueDate()
        .map(mapToOffsetDateTimeOperations("dueDate", validationErrors))
        .ifPresent(builder::dueDateOperations);
    filter
        .getFollowUpDate()
        .map(mapToOffsetDateTimeOperations("followUpDate", validationErrors))
        .ifPresent(builder::followUpDateOperations);
    if (!CollectionUtils.isEmpty(filter.getTags().orElse(null))) {
      final var tagErrors = TagsValidator.validate(filter.getTags().orElse(null));
      if (tagErrors.isEmpty()) {
        filter.getTags().ifPresent(builder::tags);
      } else {
        validationErrors.addAll(tagErrors);
      }
    }
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }
}
