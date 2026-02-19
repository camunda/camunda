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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserTaskEntity(
    Long userTaskKey,
    String elementId,
    String name,
    String processDefinitionId,
    String processName,
    OffsetDateTime creationDate,
    OffsetDateTime completionDate,
    String assignee,
    UserTaskState state,
    Long formKey,
    Long processDefinitionKey,
    Long processInstanceKey,
    Long rootProcessInstanceKey,
    Long elementInstanceKey,
    String tenantId,
    OffsetDateTime dueDate,
    OffsetDateTime followUpDate,
    List<String> candidateGroups,
    List<String> candidateUsers,
    String externalFormReference,
    Integer processDefinitionVersion,
    Map<String, String> customHeaders,
    Integer priority,
    Set<String> tags)
    implements TenantOwnedEntity {

  public UserTaskEntity {
    // Mutable collections are required: MyBatis hydrates collection-mapped fields (e.g. from a
    // <collection> result map or a LEFT JOIN) by calling .add() on the existing instance.
    // Immutable defaults (e.g. List.of()) would cause UnsupportedOperationException at runtime.
    candidateGroups = candidateGroups != null ? candidateGroups : new ArrayList<>();
    candidateUsers = candidateUsers != null ? candidateUsers : new ArrayList<>();
    tags = tags != null ? tags : new HashSet<>();
  }

  public UserTaskEntity withName(final String newName) {
    return new UserTaskEntity(
        userTaskKey,
        elementId,
        newName,
        processDefinitionId,
        processName,
        creationDate,
        completionDate,
        assignee,
        state,
        formKey,
        processDefinitionKey,
        processInstanceKey,
        rootProcessInstanceKey,
        elementInstanceKey,
        tenantId,
        dueDate,
        followUpDate,
        candidateGroups,
        candidateUsers,
        externalFormReference,
        processDefinitionVersion,
        customHeaders,
        priority,
        tags);
  }

  public UserTaskEntity withProcessName(final String newProcessName) {
    return new UserTaskEntity(
        userTaskKey,
        elementId,
        name,
        processDefinitionId,
        newProcessName,
        creationDate,
        completionDate,
        assignee,
        state,
        formKey,
        processDefinitionKey,
        processInstanceKey,
        rootProcessInstanceKey,
        elementInstanceKey,
        tenantId,
        dueDate,
        followUpDate,
        candidateGroups,
        candidateUsers,
        externalFormReference,
        processDefinitionVersion,
        customHeaders,
        priority,
        tags);
  }

  public boolean hasName() {
    return name != null && !name.isBlank();
  }

  public boolean hasProcessName() {
    return !StringUtils.isBlank(processName);
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
