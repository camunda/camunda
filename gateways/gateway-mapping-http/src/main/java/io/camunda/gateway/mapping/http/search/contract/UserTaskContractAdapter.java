/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.ResponseMapper.formatDate;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import io.camunda.gateway.protocol.model.UserTaskResult;
import io.camunda.gateway.protocol.model.UserTaskStateEnum;
import io.camunda.search.entities.UserTaskEntity;
import java.util.List;

public final class UserTaskContractAdapter {

  private UserTaskContractAdapter() {}

  public static List<UserTaskResult> adapt(final List<UserTaskEntity> entities) {
    return entities.stream().map(UserTaskContractAdapter::adapt).toList();
  }

  public static UserTaskResult adapt(final UserTaskEntity entity) {
    return new UserTaskResult()
        .state(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(entity.state(), UserTaskStateEnum::fromValue),
                "state",
                entity))
        .elementId(ContractPolicy.requireNonNull(entity.elementId(), "elementId", entity))
        .candidateGroups(
            ContractPolicy.requireNonNull(entity.candidateGroups(), "candidateGroups", entity))
        .candidateUsers(
            ContractPolicy.requireNonNull(entity.candidateUsers(), "candidateUsers", entity))
        .processDefinitionId(
            ContractPolicy.requireNonNull(
                entity.processDefinitionId(), "processDefinitionId", entity))
        .creationDate(
            ContractPolicy.requireNonNull(
                formatDate(entity.creationDate()), "creationDate", entity))
        .tenantId(ContractPolicy.requireNonNull(entity.tenantId(), "tenantId", entity))
        .processDefinitionVersion(
            ContractPolicy.requireNonNull(
                entity.processDefinitionVersion(), "processDefinitionVersion", entity))
        .customHeaders(
            ContractPolicy.requireNonNull(entity.customHeaders(), "customHeaders", entity))
        .priority(ContractPolicy.requireNonNull(entity.priority(), "priority", entity))
        .userTaskKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.userTaskKey()), "userTaskKey", entity))
        .elementInstanceKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.elementInstanceKey()), "elementInstanceKey", entity))
        .processDefinitionKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.processDefinitionKey()), "processDefinitionKey", entity))
        .processInstanceKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.processInstanceKey()), "processInstanceKey", entity))
        .tags(ContractPolicy.requireNonNull(entity.tags(), "tags", entity))
        .name(entity.name())
        .assignee(entity.assignee())
        .completionDate(formatDate(entity.completionDate()))
        .followUpDate(formatDate(entity.followUpDate()))
        .dueDate(formatDate(entity.dueDate()))
        .externalFormReference(entity.externalFormReference())
        .processName(entity.processName())
        .rootProcessInstanceKey(KeyUtil.keyToString(entity.rootProcessInstanceKey()))
        .formKey(KeyUtil.keyToString(entity.formKey()));
  }
}
