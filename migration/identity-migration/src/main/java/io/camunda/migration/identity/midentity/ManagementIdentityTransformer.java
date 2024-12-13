/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.midentity;

import io.camunda.migration.identity.dto.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.dto.Tenant;
import org.springframework.stereotype.Component;

@Component
public class ManagementIdentityTransformer {
  public MigrationStatusUpdateRequest toMigrationStatusUpdateRequest(
      final Tenant tenant, final Exception e) {
    return new MigrationStatusUpdateRequest(
        tenant.tenantId(),
        MigrationEntityType.TENANT,
        null,
        e == null,
        e == null ? null : e.getMessage());
  }
}
