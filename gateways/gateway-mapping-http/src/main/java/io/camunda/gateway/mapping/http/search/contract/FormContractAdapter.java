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
import io.camunda.gateway.protocol.model.Form;
import io.camunda.search.entities.FormEntity;

public final class FormContractAdapter {

  private FormContractAdapter() {}

  public static Form adapt(final FormEntity entity) {
    return new Form()
        .tenantId(ContractPolicy.requireNonNull(entity.tenantId(), "tenantId", entity))
        .formId(ContractPolicy.requireNonNull(entity.formId(), "formId", entity))
        .schema(ContractPolicy.requireNonNull(entity.schema(), "schema", entity))
        .version(ContractPolicy.requireNonNull(entity.version(), "version", entity))
        .formKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.formKey()), "formKey", entity));
  }
}
