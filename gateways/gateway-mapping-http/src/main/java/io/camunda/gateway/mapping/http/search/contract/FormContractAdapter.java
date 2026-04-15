/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.search.contract.generated.FormContract.Fields;

import io.camunda.gateway.mapping.http.search.contract.generated.FormContract;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.search.entities.FormEntity;

public final class FormContractAdapter {

  private FormContractAdapter() {}

  public static FormContract adapt(final FormEntity entity) {
    return FormContract.builder()
        .tenantId(ContractPolicy.requireNonNull(entity.tenantId(), Fields.TENANT_ID, entity))
        .formId(ContractPolicy.requireNonNull(entity.formId(), Fields.FORM_ID, entity))
        .schema(ContractPolicy.requireNonNull(entity.schema(), Fields.SCHEMA, entity))
        .version(ContractPolicy.requireNonNull(entity.version(), Fields.VERSION, entity))
        .formKey(ContractPolicy.requireNonNull(entity.formKey(), Fields.FORM_KEY, entity))
        .build();
  }
}
