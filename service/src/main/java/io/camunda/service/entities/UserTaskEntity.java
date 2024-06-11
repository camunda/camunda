/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.service.enums.UserTaskState;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public final record UserTaskEntity(
    Long key,
    String name,
    String taskDefinitionId,
    String processName,
    String creationDate,
    String completionDate,
    String assignee,
    UserTaskState taskState,
    List<String> sortValues,
    boolean isFirst,
    Long formKey,
    String formId,
    int formVersion,
    boolean isFormEmbedded,
    String processDefinitionKey,
    String processInstanceKey,
    String tenantId,
    String dueDate,
    String followUpDate,
    List<String> candidateGroups,
    List<String> candidateUsers,
    List<Variable> variables) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final record Variable(
      String id, String name, String value, boolean isValueTruncated, String previewValue) {}
}
