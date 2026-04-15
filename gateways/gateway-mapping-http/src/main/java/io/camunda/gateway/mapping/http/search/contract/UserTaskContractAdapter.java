/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.ResponseMapper.formatDate;
import static io.camunda.gateway.mapping.http.search.contract.generated.UserTaskContract.Fields;

import io.camunda.gateway.mapping.http.search.contract.generated.UserTaskContract;
import io.camunda.gateway.mapping.http.search.contract.generated.UserTaskStateEnum;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.search.entities.UserTaskEntity;
import java.util.List;

public final class UserTaskContractAdapter {

  private UserTaskContractAdapter() {}

  public static List<UserTaskContract> adapt(final List<UserTaskEntity> entities) {
    return entities.stream().map(UserTaskContractAdapter::adapt).toList();
  }

  public static UserTaskContract adapt(final UserTaskEntity entity) {
    return UserTaskContract.builder()
        .state(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(entity.state(), UserTaskStateEnum::fromValue),
                Fields.STATE,
                entity))
        .elementId(ContractPolicy.requireNonNull(entity.elementId(), Fields.ELEMENT_ID, entity))
        .candidateGroups(
            ContractPolicy.requireNonNull(
                entity.candidateGroups(), Fields.CANDIDATE_GROUPS, entity))
        .candidateUsers(
            ContractPolicy.requireNonNull(entity.candidateUsers(), Fields.CANDIDATE_USERS, entity))
        .processDefinitionId(
            ContractPolicy.requireNonNull(
                entity.processDefinitionId(), Fields.PROCESS_DEFINITION_ID, entity))
        .creationDate(
            ContractPolicy.requireNonNull(
                formatDate(entity.creationDate()), Fields.CREATION_DATE, entity))
        .tenantId(ContractPolicy.requireNonNull(entity.tenantId(), Fields.TENANT_ID, entity))
        .processDefinitionVersion(
            ContractPolicy.requireNonNull(
                entity.processDefinitionVersion(), Fields.PROCESS_DEFINITION_VERSION, entity))
        .customHeaders(
            ContractPolicy.requireNonNull(entity.customHeaders(), Fields.CUSTOM_HEADERS, entity))
        .priority(ContractPolicy.requireNonNull(entity.priority(), Fields.PRIORITY, entity))
        .userTaskKey(
            ContractPolicy.requireNonNull(entity.userTaskKey(), Fields.USER_TASK_KEY, entity))
        .elementInstanceKey(
            ContractPolicy.requireNonNull(
                entity.elementInstanceKey(), Fields.ELEMENT_INSTANCE_KEY, entity))
        .processDefinitionKey(
            ContractPolicy.requireNonNull(
                entity.processDefinitionKey(), Fields.PROCESS_DEFINITION_KEY, entity))
        .processInstanceKey(
            ContractPolicy.requireNonNull(
                entity.processInstanceKey(), Fields.PROCESS_INSTANCE_KEY, entity))
        .tags(ContractPolicy.requireNonNull(entity.tags(), Fields.TAGS, entity))
        .name(entity.name())
        .assignee(entity.assignee())
        .completionDate(formatDate(entity.completionDate()))
        .followUpDate(formatDate(entity.followUpDate()))
        .dueDate(formatDate(entity.dueDate()))
        .externalFormReference(entity.externalFormReference())
        .processName(entity.processName())
        .rootProcessInstanceKey(entity.rootProcessInstanceKey())
        .formKey(entity.formKey())
        .build();
  }
}
