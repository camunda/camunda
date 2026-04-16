/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy.requireNonNull;
import static io.camunda.gateway.mapping.http.util.KeyUtil.keyToString;

import io.camunda.gateway.protocol.model.FormResult;
import io.camunda.search.entities.FormEntity;

public final class FormContractAdapter {

  private FormContractAdapter() {}

  public static FormResult adapt(final FormEntity entity) {
    return new FormResult()
        .tenantId(requireNonNull(entity.tenantId(), "tenantId", entity))
        .formId(requireNonNull(entity.formId(), "formId", entity))
        .schema(requireNonNull(entity.schema(), "schema", entity))
        .version(requireNonNull(entity.version(), "version", entity))
        .formKey(requireNonNull(keyToString(entity.formKey()), "formKey", entity));
  }
}
