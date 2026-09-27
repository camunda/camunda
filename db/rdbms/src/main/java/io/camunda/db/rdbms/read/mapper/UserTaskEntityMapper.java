/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import static io.camunda.db.rdbms.read.NullSafeStrings.nullToEmpty;

import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.db.rdbms.write.util.MapSerializer;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.UserTaskEntity.UserTaskState;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class UserTaskEntityMapper {

  public static UserTaskEntity toEntity(final UserTaskDbModel dbModel) {
    return new UserTaskEntity(
        dbModel.userTaskKey(),
        nullToEmpty(dbModel.elementId()),
        dbModel.name(),
        nullToEmpty(dbModel.processDefinitionId()),
        null, // filled later from ProcessCache in UserTaskService
        dbModel.creationDate(),
        dbModel.completionDate(),
        dbModel.assignee(),
        UserTaskState.valueOf(dbModel.state().name()),
        dbModel.formKey(),
        dbModel.processDefinitionKey(),
        dbModel.processInstanceKey(),
        dbModel.rootProcessInstanceKey(),
        dbModel.businessId(),
        dbModel.elementInstanceKey(),
        nullToEmpty(dbModel.tenantId()),
        dbModel.dueDate(),
        dbModel.followUpDate(),
        // ensure candidate groups and users have a well-defined order
        sortedList(dbModel.candidateGroups()),
        sortedList(dbModel.candidateUsers()),
        dbModel.externalFormReference(),
        dbModel.processDefinitionVersion(),
        MapSerializer.deserialize(dbModel.serializedCustomHeaders()),
        dbModel.priority(),
        dbModel.tags());
  }

  private static List<String> sortedList(final List<String> list) {
    if (list == null) {
      return null;
    }
    return list.stream().sorted().collect(Collectors.toCollection(ArrayList::new));
  }
}
