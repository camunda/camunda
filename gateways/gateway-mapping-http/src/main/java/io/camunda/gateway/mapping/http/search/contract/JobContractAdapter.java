/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.ResponseMapper.formatDate;
import static io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobSearchStrictContract.Fields;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobKindEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobListenerEventTypeEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobSearchStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobStateEnum;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.search.entities.JobEntity;
import java.util.List;

public final class JobContractAdapter {

  private JobContractAdapter() {}

  public static List<GeneratedJobSearchStrictContract> adapt(final List<JobEntity> entities) {
    return entities.stream().map(JobContractAdapter::adapt).toList();
  }

  public static GeneratedJobSearchStrictContract adapt(final JobEntity entity) {
    return GeneratedJobSearchStrictContract.builder()
        .customHeaders(
            ContractPolicy.requireNonNull(entity.customHeaders(), Fields.CUSTOM_HEADERS, entity))
        .elementInstanceKey(
            ContractPolicy.requireNonNull(
                entity.elementInstanceKey(), Fields.ELEMENT_INSTANCE_KEY, entity))
        .hasFailedWithRetriesLeft(
            ContractPolicy.requireNonNull(
                entity.hasFailedWithRetriesLeft(), Fields.HAS_FAILED_WITH_RETRIES_LEFT, entity))
        .jobKey(ContractPolicy.requireNonNull(entity.jobKey(), Fields.JOB_KEY, entity))
        .kind(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(entity.kind(), GeneratedJobKindEnum::fromValue),
                Fields.KIND,
                entity))
        .listenerEventType(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(
                    entity.listenerEventType(), GeneratedJobListenerEventTypeEnum::fromValue),
                Fields.LISTENER_EVENT_TYPE,
                entity))
        .processDefinitionId(
            ContractPolicy.requireNonNull(
                entity.processDefinitionId(), Fields.PROCESS_DEFINITION_ID, entity))
        .processDefinitionKey(
            ContractPolicy.requireNonNull(
                entity.processDefinitionKey(), Fields.PROCESS_DEFINITION_KEY, entity))
        .processInstanceKey(
            ContractPolicy.requireNonNull(
                entity.processInstanceKey(), Fields.PROCESS_INSTANCE_KEY, entity))
        .retries(ContractPolicy.requireNonNull(entity.retries(), Fields.RETRIES, entity))
        .state(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(entity.state(), GeneratedJobStateEnum::fromValue),
                Fields.STATE,
                entity))
        .tenantId(ContractPolicy.requireNonNull(entity.tenantId(), Fields.TENANT_ID, entity))
        .type(ContractPolicy.requireNonNull(entity.type(), Fields.TYPE, entity))
        .worker(ContractPolicy.requireNonNull(entity.worker(), Fields.WORKER, entity))
        .deadline(formatDate(entity.deadline()))
        .deniedReason(entity.deniedReason())
        .elementId(entity.elementId())
        .endTime(formatDate(entity.endTime()))
        .errorCode(entity.errorCode())
        .errorMessage(entity.errorMessage())
        .isDenied(entity.isDenied())
        .rootProcessInstanceKey(entity.rootProcessInstanceKey())
        .creationTime(formatDate(entity.creationTime()))
        .lastUpdateTime(formatDate(entity.lastUpdateTime()))
        .build();
  }
}
