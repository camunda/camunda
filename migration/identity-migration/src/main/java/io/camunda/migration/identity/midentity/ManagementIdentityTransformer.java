/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.midentity;

import io.camunda.migration.identity.dto.Client;
import io.camunda.migration.identity.dto.Group;
import io.camunda.migration.identity.dto.MappingRule;
import io.camunda.migration.identity.dto.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.dto.Role;
import io.camunda.migration.identity.dto.Tenant;
import io.camunda.migration.identity.dto.UserTenants;

public class ManagementIdentityTransformer {
  public static MigrationStatusUpdateRequest toMigrationStatusUpdateRequest(
      final Tenant tenant, final Exception e) {
    return new MigrationStatusUpdateRequest(
        tenant.tenantId(),
        MigrationEntityType.TENANT,
        null,
        e == null,
        e == null ? null : e.getMessage());
  }

  public static MigrationStatusUpdateRequest toMigrationStatusUpdateRequest(
      final MappingRule mappingRule, final Exception e) {
    return new MigrationStatusUpdateRequest(
        mappingRule.getName(),
        MigrationEntityType.MAPPING_RULE,
        null,
        e == null,
        e == null ? null : e.getMessage());
  }

  public static MigrationStatusUpdateRequest toMigrationStatusUpdateRequest(
      final UserTenants tenantUser, final Exception e) {
    return new MigrationStatusUpdateRequest(
        tenantUser.id(),
        MigrationEntityType.TENANT_USER,
        null,
        e == null,
        e == null ? null : e.getMessage());
  }

  public static MigrationStatusUpdateRequest toMigrationStatusUpdateRequest(
      final Group group, final Exception e) {
    return new MigrationStatusUpdateRequest(
        group.id(), MigrationEntityType.GROUP, null, e == null, e == null ? null : e.getMessage());
  }

  public static MigrationStatusUpdateRequest toMigrationStatusUpdateRequest(
      final Role role, final Exception e) {
    return new MigrationStatusUpdateRequest(
        role.name(), MigrationEntityType.ROLE, null, e == null, e == null ? null : e.getMessage());
  }

  public static MigrationStatusUpdateRequest toMigrationStatusUpdateRequest(
      final Client client, final Exception e) {
    return new MigrationStatusUpdateRequest(
        client.clientId(),
        MigrationEntityType.CLIENT,
        null,
        e == null,
        e == null ? null : e.getMessage());
  }
}
