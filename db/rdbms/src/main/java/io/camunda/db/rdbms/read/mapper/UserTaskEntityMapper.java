/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.UserTaskEntity.UserTaskState;
import java.util.HashMap;

public class UserTaskEntityMapper {

  public static UserTaskEntity toEntity(final UserTaskDbModel dbModel) {
    final var objectMapper = new ObjectMapper();

    HashMap<String, String> customHeader = new HashMap<>();
    if (dbModel.serializedCustomHeaders() != null && !dbModel.serializedCustomHeaders().isEmpty()) {
      try {
        customHeader = objectMapper.readValue(dbModel.serializedCustomHeaders(), HashMap.class);
      } catch (final Exception e) {
        throw new RuntimeException("Failed to deserialize custom headers", e);
      }
    }

    return new UserTaskEntity(
        dbModel.key(),
        dbModel.flowNodeBpmnId(),
        dbModel.processDefinitionId(),
        ofNullable(dbModel.creationTime()).map(Object::toString).orElse(null),
        ofNullable(dbModel.completionTime()).map(Object::toString).orElse(null),
        dbModel.assignee(),
        UserTaskState.valueOf(dbModel.state().name()),
        dbModel.formKey(),
        dbModel.processDefinitionKey(),
        dbModel.processInstanceKey(),
        dbModel.elementInstanceKey(),
        dbModel.tenantId(),
        ofNullable(dbModel.dueDate()).map(Object::toString).orElse(null),
        ofNullable(dbModel.followUpDate()).map(Object::toString).orElse(null),
        dbModel.candidateGroups(),
        dbModel.candidateUsers(),
        dbModel.externalFormReference(),
        dbModel.processDefinitionVersion(),
        customHeader,
        dbModel.priority());
  }
}
