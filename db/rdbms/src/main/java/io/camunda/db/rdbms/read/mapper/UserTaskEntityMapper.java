/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.db.rdbms.write.util.CustomHeaderSerializer;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.UserTaskEntity.UserTaskState;

public class UserTaskEntityMapper {

  public static UserTaskEntity toEntity(final UserTaskDbModel dbModel) {
    return new UserTaskEntity(
        dbModel.userTaskKey(),
        dbModel.elementId(),
        dbModel.name(),
        dbModel.processDefinitionId(),
        null, // filled later from ProcessCache in UserTaskService
        dbModel.creationDate(),
        dbModel.completionDate(),
        dbModel.assignee(),
        UserTaskState.valueOf(dbModel.state().name()),
        dbModel.formKey(),
        dbModel.processDefinitionKey(),
        dbModel.processInstanceKey(),
        dbModel.rootProcessInstanceKey(),
        dbModel.elementInstanceKey(),
        dbModel.tenantId(),
        dbModel.dueDate(),
        dbModel.followUpDate(),
        dbModel.candidateGroups(),
        dbModel.candidateUsers(),
        dbModel.externalFormReference(),
        dbModel.processDefinitionVersion(),
        CustomHeaderSerializer.deserialize(dbModel.serializedCustomHeaders()),
        dbModel.priority(),
        dbModel.tags());
  }
}
