/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import io.camunda.migration.identity.dto.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.dto.Tenant;
import io.camunda.migration.identity.dto.TenantMappingRule;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.migration.identity.midentity.ManagementIdentityTransformer;
import io.camunda.migration.identity.service.MappingService;
import io.camunda.service.TenantServices;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TenantMappingRuleMigrationHandler implements MigrationHandler {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(TenantMappingRuleMigrationHandler.class);
  private final ManagementIdentityClient managementIdentityClient;
  private final ManagementIdentityTransformer managementIdentityTransformer;
  private final TenantServices tenantServices;
  private final MappingService mappingService;

  public TenantMappingRuleMigrationHandler(
      final ManagementIdentityClient managementIdentityClient,
      final ManagementIdentityTransformer managementIdentityTransformer,
      final TenantServices tenantServices,
      final MappingService mappingService) {
    this.managementIdentityClient = managementIdentityClient;
    this.managementIdentityTransformer = managementIdentityTransformer;
    this.tenantServices = tenantServices;
    this.mappingService = mappingService;
  }

  @Override
  public void migrate() {
    LOGGER.debug("Migrating tenant mapping rules");
    List<TenantMappingRule> tenantMappingRules;
    do {
      tenantMappingRules = managementIdentityClient.fetchTenantMappingRules(SIZE);
      managementIdentityClient.updateMigrationStatus(
          tenantMappingRules.stream().map(this::createTenantMappingRule).toList());
    } while (!tenantMappingRules.isEmpty());
    LOGGER.debug("Finished migrating tenant mapping rules");
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
        final var tenant = tenantServices.getById(mappingTenant.tenantId());
        assignMappingToTenant(tenant.key(), mappingKey);
      }
      return managementIdentityTransformer.toMigrationStatusUpdateRequest(tenantMappingRule, null);
    } catch (final Exception e) {
      LOGGER.error("Error creating tenant mapping rule", e);
      return managementIdentityTransformer.toMigrationStatusUpdateRequest(tenantMappingRule, e);
    }
  }

  private void assignMappingToTenant(final long tenantKey, final long mappingKey) {
    try {
      tenantServices.addMember(tenantKey, EntityType.MAPPING, mappingKey);
    } catch (final Exception e) {
      if (!isConflictError(e)) {
        throw e;
      }
    }
  }
}
