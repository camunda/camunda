/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.search.contract.generated.ProcessDefinitionContract.Fields;

import io.camunda.gateway.mapping.http.search.contract.generated.ProcessDefinitionContract;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.search.entities.ProcessDefinitionEntity;

/**
 * Contract adaptation layer for ProcessDefinition.
 *
 * <p>POC demonstration #1 for reviewers: keep business policy in a hand-written adapter while the
 * generated strict contract encapsulates deterministic coercion/validation.
 *
 * <p>In this slice, policy includes required field enforcement, automatic coercion of Java.long to
 * String for entity keys, and deriving hasStartForm from formId blankness.
 */
public final class ProcessDefinitionContractAdapter {

  private ProcessDefinitionContractAdapter() {}

  public static ProcessDefinitionContract adapt(final ProcessDefinitionEntity entity) {
    return ProcessDefinitionContract.builder()
        .resourceName(
            ContractPolicy.requireNonNull(entity.resourceName(), Fields.RESOURCE_NAME, entity))
        .version(ContractPolicy.requireNonNull(entity.version(), Fields.VERSION, entity))
        .processDefinitionId(
            ContractPolicy.requireNonNull(
                entity.processDefinitionId(), Fields.PROCESS_DEFINITION_ID, entity))
        .tenantId(ContractPolicy.requireNonNull(entity.tenantId(), Fields.TENANT_ID, entity))
        .processDefinitionKey(
            ContractPolicy.requireNonNull(
                entity.processDefinitionKey(), Fields.PROCESS_DEFINITION_KEY, entity))
        .hasStartForm(
            ContractPolicy.requireNonNull(
                ContractPolicy.isNotBlank(entity.formId()), Fields.HAS_START_FORM, entity))
        .name(entity.name()) // required + nullable in contract
        .versionTag(entity.versionTag()) // required + nullable in contract
        .build();
  }
}
