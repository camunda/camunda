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
import io.camunda.gateway.protocol.model.JobKindEnum;
import io.camunda.gateway.protocol.model.JobListenerEventTypeEnum;
import io.camunda.gateway.protocol.model.JobSearchResult;
import io.camunda.gateway.protocol.model.JobStateEnum;
import io.camunda.search.entities.JobEntity;
import java.util.List;

public final class JobContractAdapter {

  private JobContractAdapter() {}

  public static List<JobSearchResult> adapt(final List<JobEntity> entities) {
    return entities.stream().map(JobContractAdapter::adapt).toList();
  }

  public static JobSearchResult adapt(final JobEntity entity) {
    return new JobSearchResult()
        .customHeaders(
            ContractPolicy.requireNonNull(entity.customHeaders(), "customHeaders", entity))
        .elementInstanceKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.elementInstanceKey()), "elementInstanceKey", entity))
        .hasFailedWithRetriesLeft(
            ContractPolicy.requireNonNull(
                entity.hasFailedWithRetriesLeft(), "hasFailedWithRetriesLeft", entity))
        .jobKey(
            ContractPolicy.requireNonNull(KeyUtil.keyToString(entity.jobKey()), "jobKey", entity))
        .kind(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(entity.kind(), JobKindEnum::fromValue), "kind", entity))
        .listenerEventType(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(
                    entity.listenerEventType(), JobListenerEventTypeEnum::fromValue),
                "listenerEventType",
                entity))
        .processDefinitionId(
            ContractPolicy.requireNonNull(
                entity.processDefinitionId(), "processDefinitionId", entity))
        .processDefinitionKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.processDefinitionKey()), "processDefinitionKey", entity))
        .processInstanceKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.processInstanceKey()), "processInstanceKey", entity))
        .retries(ContractPolicy.requireNonNull(entity.retries(), "retries", entity))
        .state(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(entity.state(), JobStateEnum::fromValue), "state", entity))
        .tenantId(ContractPolicy.requireNonNull(entity.tenantId(), "tenantId", entity))
        .type(ContractPolicy.requireNonNull(entity.type(), "type", entity))
        .worker(ContractPolicy.requireNonNull(entity.worker(), "worker", entity))
        .deadline(formatDate(entity.deadline()))
        .deniedReason(entity.deniedReason())
        .elementId(entity.elementId())
        .endTime(formatDate(entity.endTime()))
        .errorCode(entity.errorCode())
        .errorMessage(entity.errorMessage())
        .isDenied(entity.isDenied())
        .rootProcessInstanceKey(KeyUtil.keyToString(entity.rootProcessInstanceKey()))
        .creationTime(formatDate(entity.creationTime()))
        .lastUpdateTime(formatDate(entity.lastUpdateTime()));
  }
}
