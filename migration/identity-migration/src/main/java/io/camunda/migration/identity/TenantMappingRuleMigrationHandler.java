/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import io.camunda.migration.identity.dto.Tenant;
import io.camunda.migration.identity.dto.TenantMappingRule;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.migration.identity.midentity.ManagementIdentityTransformer;
import io.camunda.migration.identity.midentity.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.service.MappingService;
import io.camunda.migration.identity.service.TenantService;
import io.camunda.search.entities.TenantEntity;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TenantMappingRuleMigrationHandler implements MigrationHandler {

  public static final int SIZE = 100;
  private final ManagementIdentityClient managementIdentityClient;
  private final ManagementIdentityTransformer managementIdentityTransformer;
  private final TenantService tenantService;
  private final MappingService mappingService;

  public TenantMappingRuleMigrationHandler(
      final ManagementIdentityClient managementIdentityClient,
      final ManagementIdentityTransformer managementIdentityTransformer,
      final TenantService tenantService,
      final MappingService mappingService) {
    this.managementIdentityClient = managementIdentityClient;
    this.managementIdentityTransformer = managementIdentityTransformer;
    this.tenantService = tenantService;
    this.mappingService = mappingService;
  }

  @Override
  public void migrate() {
    TenantMappingRule lastTenantMappingRule = null;
    do {
      final List<TenantMappingRule> tenantMappingRules =
          managementIdentityClient.fetchTenantMappingRules(lastTenantMappingRule, SIZE);
      managementIdentityClient.updateMigrationStatus(
          tenantMappingRules.stream().map(this::createTenantMappingRule).toList());
      lastTenantMappingRule =
          tenantMappingRules.isEmpty()
              ? null
              : tenantMappingRules.get(tenantMappingRules.size() - 1);
    } while (lastTenantMappingRule != null);
  }

  private MigrationStatusUpdateRequest createTenantMappingRule(
      final TenantMappingRule tenantMappingRule) {
    try {
      final var mappingKey =
          mappingService.findOrCreateMapping(
              tenantMappingRule.getName(),
              tenantMappingRule.getClaimName(),
              tenantMappingRule.getClaimValue());
      for (final Tenant mappingTenant : tenantMappingRule.getAppliedTenants()) {
        final var tenant =
            tenantService.fetchOrCreateTenant(mappingTenant.tenantId(), mappingTenant.name());
        assignMappingToTenant(tenant, mappingKey);
      }
      return managementIdentityTransformer.toMigrationStatusUpdateRequest(tenantMappingRule, null);
    } catch (final Exception e) {
      return managementIdentityTransformer.toMigrationStatusUpdateRequest(tenantMappingRule, e);
    }
  }

  private void assignMappingToTenant(final TenantEntity tenant, final Long mappingKey) {
    try {
      tenantService.assignMemberToTenant(tenant.key(), mappingKey);
    } catch (final Exception e) {
      if (!isConflictError(e)) {
        throw e;
      }
    }
  }
}
