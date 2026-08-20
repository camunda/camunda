/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.appint.event;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public sealed interface Event {

  record EventMetaData(String id, String intent, String type) {}

  record ProcessMetaData(
      String processDefinitionKey,
      String processDefinitionId,
      String processDefinitionVersion,
      String processInstanceKey,
      String elementId,
      String elementInstanceKey,
      String tenantId) {}

  record UserTaskMetaData(
      String userTaskKey,
      Set<String> tags,
      Map<String, String> customHeaders,
      String assignee,
      List<String> candidateGroups,
      List<String> candidateUsers,
      String externalFormReference,
      Integer priority,
      String formKey,
      OffsetDateTime createdAt,
      String dueDate,
      String followUpDate) {}

  record UserTaskEvent(
      @JsonUnwrapped EventMetaData eventMetaData,
      @JsonUnwrapped UserTaskMetaData userTaskMetaData,
      @JsonUnwrapped ProcessMetaData processMetaData)
      implements Event {}
}
