/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.ResponseMapper.formatDate;
import static io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy.mapEnum;
import static io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy.requireNonNull;

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
            requireNonNull(mapEnum(entity.state(), UserTaskStateEnum::fromValue), "state", entity))
        .elementId(requireNonNull(entity.elementId(), "elementId", entity))
        .candidateGroups(requireNonNull(entity.candidateGroups(), "candidateGroups", entity))
        .candidateUsers(requireNonNull(entity.candidateUsers(), "candidateUsers", entity))
        .processDefinitionId(
            requireNonNull(entity.processDefinitionId(), "processDefinitionId", entity))
        .creationDate(requireNonNull(formatDate(entity.creationDate()), "creationDate", entity))
        .tenantId(requireNonNull(entity.tenantId(), "tenantId", entity))
        .processDefinitionVersion(
            requireNonNull(entity.processDefinitionVersion(), "processDefinitionVersion", entity))
        .customHeaders(requireNonNull(entity.customHeaders(), "customHeaders", entity))
        .priority(requireNonNull(entity.priority(), "priority", entity))
        .userTaskKey(
            requireNonNull(KeyUtil.keyToString(entity.userTaskKey()), "userTaskKey", entity))
        .elementInstanceKey(
            requireNonNull(
                KeyUtil.keyToString(entity.elementInstanceKey()), "elementInstanceKey", entity))
        .processDefinitionKey(
            requireNonNull(
                KeyUtil.keyToString(entity.processDefinitionKey()), "processDefinitionKey", entity))
        .processInstanceKey(
            requireNonNull(
                KeyUtil.keyToString(entity.processInstanceKey()), "processInstanceKey", entity))
        .tags(requireNonNull(entity.tags(), "tags", entity))
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
