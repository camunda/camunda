/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.UserTaskEntity.UserTaskState;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import java.util.List;

public class UserTaskEntityTransformer implements ServiceTransformer<TaskEntity, UserTaskEntity> {

  @Override
  public UserTaskEntity apply(final TaskEntity source) {
    return new UserTaskEntity(
        source.getKey(),
        source.getFlowNodeBpmnId(),
        source.getName(),
        source.getBpmnProcessId(),
        null, // filled later from ProcessCache in UserTaskService
        source.getCreationTime(),
        source.getCompletionTime(),
        source.getAssignee(),
        toUserTaskState(source.getState()),
        asLong(source.getFormKey()),
        asLong(source.getProcessDefinitionId()),
        asLong(source.getProcessInstanceId()),
        asLong(source.getFlowNodeInstanceId()),
        source.getTenantId(),
        source.getDueDate(),
        source.getFollowUpDate(),
        asList(source.getCandidateGroups()),
        asList(source.getCandidateUsers()),
        source.getExternalFormReference(),
        source.getProcessDefinitionVersion(),
        source.getCustomHeaders(),
        source.getPriority(),
        source.getTags());
  }

  private UserTaskState toUserTaskState(final TaskState source) {
    if (source == null) {
      return null;
    }
    return switch (source) {
      case CREATING -> UserTaskState.CREATING;
      case CREATED -> UserTaskState.CREATED;
      case ASSIGNING -> UserTaskState.ASSIGNING;
      case UPDATING -> UserTaskState.UPDATING;
      case COMPLETING -> UserTaskState.COMPLETING;
      case COMPLETED -> UserTaskState.COMPLETED;
      case CANCELING -> UserTaskState.CANCELING;
      case CANCELED -> UserTaskState.CANCELED;
      case FAILED -> UserTaskState.FAILED;
    };
  }

  private Long asLong(final String value) {
    return value == null ? null : Long.valueOf(value);
  }

  private <T> List<T> asList(final T[] values) {
    return values == null ? null : List.of(values);
  }
}
