/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.AdvancedSecretStatusFilter;
import io.camunda.gateway.protocol.model.SecretStatusEnum;
import io.camunda.gateway.protocol.model.SecretStatusFilterProperty;

public class SecretStatusFilterPropertyDeserializer
    extends FilterDeserializer<SecretStatusFilterProperty, SecretStatusEnum> {

  @Override
  protected Class<? extends SecretStatusFilterProperty> getFinalType() {
    return AdvancedSecretStatusFilter.class;
  }

  @Override
  protected Class<SecretStatusEnum> getImplicitValueType() {
    return SecretStatusEnum.class;
  }

  @Override
  protected SecretStatusFilterProperty createFromImplicitValue(final SecretStatusEnum value) {
    return AdvancedSecretStatusFilter.Builder.create().$eq(value).build();
  }
}
