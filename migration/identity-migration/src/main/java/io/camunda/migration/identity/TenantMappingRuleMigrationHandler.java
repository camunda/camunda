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
import io.camunda.search.entities.MappingEntity;
import io.camunda.security.auth.Authentication;
import io.camunda.service.MappingServices;
import io.camunda.service.MappingServices.MappingDTO;
import io.camunda.service.TenantServices;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TenantMappingRuleMigrationHandler extends MigrationHandler<TenantMappingRule> {

  private final ManagementIdentityClient managementIdentityClient;
  private final ManagementIdentityTransformer managementIdentityTransformer;
  private final TenantServices tenantServices;
  private final MappingServices mappingServices;

  public TenantMappingRuleMigrationHandler(
      final Authentication authentication,
      final ManagementIdentityClient managementIdentityClient,
      final ManagementIdentityTransformer managementIdentityTransformer,
      final TenantServices tenantServices,
      final MappingServices mappingServices) {
    this.managementIdentityClient = managementIdentityClient;
    this.managementIdentityTransformer = managementIdentityTransformer;
    this.tenantServices = tenantServices.withAuthentication(authentication);
    this.mappingServices = mappingServices.withAuthentication(authentication);
  }

  @Override
  protected List<TenantMappingRule> fetchBatch() {
    return managementIdentityClient.fetchTenantMappingRules(SIZE);
  }

  @Override
  protected void process(final List<TenantMappingRule> batch) {
    managementIdentityClient.updateMigrationStatus(batch.stream().map(this::processTask).toList());
  }

  protected MigrationStatusUpdateRequest processTask(final TenantMappingRule tenantMappingRule) {
    try {
      final var request =
          new MappingDTO(
              tenantMappingRule.getClaimName(),
              tenantMappingRule.getClaimValue(),
              tenantMappingRule.getName(),
              tenantMappingRule.getName());
      final var mappingKey =
          mappingServices
              .findMapping(request)
              .map(MappingEntity::mappingKey)
              .orElseGet(() -> mappingServices.createMapping(request).join().getMappingKey());
      for (final Tenant mappingTenant : tenantMappingRule.getAppliedTenants()) {
        final var tenant = tenantServices.getById(mappingTenant.tenantId());
        assignMappingToTenant(tenant.tenantId(), mappingKey);
      }
      return managementIdentityTransformer.toMigrationStatusUpdateRequest(tenantMappingRule, null);
    } catch (final Exception e) {
      logger.error("Error creating tenant mapping rule", e);
      return managementIdentityTransformer.toMigrationStatusUpdateRequest(tenantMappingRule, e);
    }
  }

  private void assignMappingToTenant(final String tenantId, final long mappingKey) {
    try {
      tenantServices.addMember(tenantId, EntityType.MAPPING, mappingKey).join();
    } catch (final Exception e) {
      if (!isConflictError(e)) {
        throw e;
      }
    }
  }
}
