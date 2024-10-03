/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public final record UserTaskEntity(
    Long key,
    String flowNodeBpmnId,
    String bpmnProcessId,
    String creationTime,
    String completionTime,
    String assignee,
    String state,
    Long formKey,
    Long processDefinitionId, // equivalent to processKey
    Long processInstanceId, // equivalent to processInstanceKey
    Long flowNodeInstanceId,
    String tenantId,
    String dueDate,
    String followUpDate,
    List<String> candidateGroups,
    List<String> candidateUsers,
    String externalFormReference,
    Integer processDefinitionVersion,
    Map<String, String> customHeaders,
    Integer priority) {}
