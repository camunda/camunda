/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import io.camunda.gateway.protocol.model.ProcessDefinition;
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

  public static ProcessDefinition adapt(final ProcessDefinitionEntity entity) {
    return new ProcessDefinition()
        .resourceName(ContractPolicy.requireNonNull(entity.resourceName(), "resourceName", entity))
        .version(ContractPolicy.requireNonNull(entity.version(), "version", entity))
        .processDefinitionId(
            ContractPolicy.requireNonNull(
                entity.processDefinitionId(), "processDefinitionId", entity))
        .tenantId(ContractPolicy.requireNonNull(entity.tenantId(), "tenantId", entity))
        .processDefinitionKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.processDefinitionKey()), "processDefinitionKey", entity))
        .hasStartForm(
            ContractPolicy.requireNonNull(
                ContractPolicy.isNotBlank(entity.formId()), "hasStartForm", entity))
        .name(entity.name()) // required + nullable in contract
        .versionTag(entity.versionTag()); // required + nullable in contract
  }
}
