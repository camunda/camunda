/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserTaskEntity(
    Long userTaskKey,
    String elementId,
    String name,
    String processDefinitionId,
    OffsetDateTime creationDate,
    OffsetDateTime completionDate,
    String assignee,
    UserTaskState state,
    Long formKey,
    Long processDefinitionKey,
    Long processInstanceKey,
    Long elementInstanceKey,
    String tenantId,
    OffsetDateTime dueDate,
    OffsetDateTime followUpDate,
    List<String> candidateGroups,
    List<String> candidateUsers,
    String externalFormReference,
    Integer processDefinitionVersion,
    Map<String, String> customHeaders,
    Integer priority)
    implements TenantOwnedEntity {

  public UserTaskEntity withName(final String newName) {
    return new UserTaskEntity(
        userTaskKey,
        elementId,
        newName,
        processDefinitionId,
        creationDate,
        completionDate,
        assignee,
        state,
        formKey,
        processDefinitionKey,
        processInstanceKey,
        elementInstanceKey,
        tenantId,
        dueDate,
        followUpDate,
        candidateGroups,
        candidateUsers,
        externalFormReference,
        processDefinitionVersion,
        customHeaders,
        priority);
  }

  public boolean hasName() {
    return name != null && !name.isBlank();
  }

  public enum UserTaskState {
    CREATING,
    CREATED,
    ASSIGNING,
    UPDATING,
    COMPLETING,
    COMPLETED,
    CANCELING,
    CANCELED,
    FAILED
  }
}
