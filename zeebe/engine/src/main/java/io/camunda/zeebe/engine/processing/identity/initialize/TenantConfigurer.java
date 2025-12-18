/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.initialize;

import io.camunda.security.configuration.ConfiguredTenant;
import io.camunda.security.validation.TenantValidator;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.util.Either;
import java.util.List;

public class TenantConfigurer
    implements EntityInitializationConfigurer<ConfiguredTenant, TenantRecord> {

  private final TenantValidator validator;

  public TenantConfigurer(final TenantValidator validator) {
    this.validator = validator;
  }

  @Override
  public Either<List<String>, TenantRecord> configure(final ConfiguredTenant tenant) {
    final List<String> violations = validator.validate(tenant.tenantId(), tenant.name());

    if (!violations.isEmpty()) {
      return Either.left(violations);
    }

    return Either.right(mapToRecord(tenant));
  }

  private TenantRecord mapToRecord(final ConfiguredTenant tenant) {
    return new TenantRecord()
        .setTenantId(tenant.tenantId())
        .setName(tenant.name())
        .setDescription(tenant.description());
  }
}
