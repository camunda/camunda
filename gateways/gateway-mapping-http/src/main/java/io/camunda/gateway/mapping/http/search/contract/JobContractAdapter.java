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
        .customHeaders(requireNonNull(entity.customHeaders(), "customHeaders", entity))
        .elementInstanceKey(
            requireNonNull(
                KeyUtil.keyToString(entity.elementInstanceKey()), "elementInstanceKey", entity))
        .hasFailedWithRetriesLeft(
            requireNonNull(entity.hasFailedWithRetriesLeft(), "hasFailedWithRetriesLeft", entity))
        .jobKey(requireNonNull(KeyUtil.keyToString(entity.jobKey()), "jobKey", entity))
        .kind(requireNonNull(mapEnum(entity.kind(), JobKindEnum::fromValue), "kind", entity))
        .listenerEventType(
            requireNonNull(
                mapEnum(entity.listenerEventType(), JobListenerEventTypeEnum::fromValue),
                "listenerEventType",
                entity))
        .processDefinitionId(
            requireNonNull(entity.processDefinitionId(), "processDefinitionId", entity))
        .processDefinitionKey(
            requireNonNull(
                KeyUtil.keyToString(entity.processDefinitionKey()), "processDefinitionKey", entity))
        .processInstanceKey(
            requireNonNull(
                KeyUtil.keyToString(entity.processInstanceKey()), "processInstanceKey", entity))
        .retries(requireNonNull(entity.retries(), "retries", entity))
        .state(requireNonNull(mapEnum(entity.state(), JobStateEnum::fromValue), "state", entity))
        .tenantId(requireNonNull(entity.tenantId(), "tenantId", entity))
        .type(requireNonNull(entity.type(), "type", entity))
        .worker(requireNonNull(entity.worker(), "worker", entity))
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
