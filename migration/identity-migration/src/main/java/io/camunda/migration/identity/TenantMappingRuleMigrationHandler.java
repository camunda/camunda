/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import static io.camunda.migration.identity.midentity.ManagementIdentityTransformer.toMigrationStatusUpdateRequest;

import io.camunda.migration.identity.dto.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.dto.Tenant;
import io.camunda.migration.identity.dto.TenantMappingRule;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.search.entities.MappingEntity;
import io.camunda.security.auth.Authentication;
import io.camunda.service.MappingServices;
import io.camunda.service.MappingServices.MappingDTO;
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
  private final TenantServices tenantServices;
  private final MappingServices mappingServices;

  public TenantMappingRuleMigrationHandler(
      final Authentication authentication,
      final ManagementIdentityClient managementIdentityClient,
      final TenantServices tenantServices,
      final MappingServices mappingServices) {
    this.managementIdentityClient = managementIdentityClient;
    this.tenantServices = tenantServices.withAuthentication(authentication);
    this.mappingServices = mappingServices.withAuthentication(authentication);
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
      final var request =
          new MappingDTO(
              tenantMappingRule.getClaimName(),
              tenantMappingRule.getClaimValue(),
              tenantMappingRule.getName());
      final var mappingKey =
          mappingServices
              .findMapping(request)
              .map(MappingEntity::mappingKey)
              .orElseGet(() -> mappingServices.createMapping(request).join().getMappingKey());
      for (final Tenant mappingTenant : tenantMappingRule.getAppliedTenants()) {
        final var tenant = tenantServices.getById(mappingTenant.tenantId());
        assignMappingToTenant(tenant.key(), mappingKey);
      }
      return toMigrationStatusUpdateRequest(tenantMappingRule, null);
    } catch (final Exception e) {
      LOGGER.error("Error creating tenant mapping rule", e);
      return toMigrationStatusUpdateRequest(tenantMappingRule, e);
    }
  }

  private void assignMappingToTenant(final long tenantKey, final long mappingKey) {
    try {
      tenantServices.addMember(tenantKey, EntityType.MAPPING, mappingKey).join();
    } catch (final Exception e) {
      if (!isConflictError(e)) {
        throw e;
      }
    }
  }
}
