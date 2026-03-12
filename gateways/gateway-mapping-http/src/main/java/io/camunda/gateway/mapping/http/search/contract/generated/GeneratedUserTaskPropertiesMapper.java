/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.protocol.model.UserTaskProperties;
import jakarta.annotation.Generated;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedUserTaskPropertiesMapper {

  private GeneratedUserTaskPropertiesMapper() {}

  public static UserTaskProperties toProtocol(
      final GeneratedUserTaskPropertiesStrictContract source) {
    return new UserTaskProperties()
        .action(source.action())
        .assignee(source.assignee())
        .candidateGroups(source.candidateGroups())
        .candidateUsers(source.candidateUsers())
        .changedAttributes(source.changedAttributes())
        .dueDate(source.dueDate())
        .followUpDate(source.followUpDate())
        .formKey(source.formKey())
        .priority(source.priority())
        .userTaskKey(source.userTaskKey());
  }
}
