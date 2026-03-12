/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.protocol.model.UserTaskResult;
import jakarta.annotation.Generated;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedUserTaskResultMapper {

  private GeneratedUserTaskResultMapper() {}

  public static UserTaskResult toProtocol(final GeneratedUserTaskStrictContract source) {
    return new UserTaskResult()
        .name(source.name())
        .state(source.state())
        .assignee(source.assignee())
        .elementId(source.elementId())
        .candidateGroups(source.candidateGroups())
        .candidateUsers(source.candidateUsers())
        .processDefinitionId(source.processDefinitionId())
        .creationDate(source.creationDate())
        .completionDate(source.completionDate())
        .followUpDate(source.followUpDate())
        .dueDate(source.dueDate())
        .tenantId(source.tenantId())
        .externalFormReference(source.externalFormReference())
        .processDefinitionVersion(source.processDefinitionVersion())
        .customHeaders(source.customHeaders())
        .priority(source.priority())
        .userTaskKey(source.userTaskKey())
        .elementInstanceKey(source.elementInstanceKey())
        .processName(source.processName())
        .processDefinitionKey(source.processDefinitionKey())
        .processInstanceKey(source.processInstanceKey())
        .rootProcessInstanceKey(source.rootProcessInstanceKey())
        .formKey(source.formKey())
        .tags(source.tags());
  }
}
