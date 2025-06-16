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
import io.camunda.service.TenantServices.TenantMemberRequest;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;

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

  // TODO: this will be revisited
  @Override
  protected List<TenantMappingRule> fetchBatch(final int page) {
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
      final var mappingId =
          mappingServices
              .findMapping(request)
              .map(MappingEntity::mappingId)
              .orElseGet(() -> mappingServices.createMapping(request).join().getMappingId());
      for (final Tenant mappingTenant : tenantMappingRule.getAppliedTenants()) {
        final var tenant = tenantServices.getById(mappingTenant.tenantId());
        assignMappingToTenant(tenant.tenantId(), mappingId);
      }
      return managementIdentityTransformer.toMigrationStatusUpdateRequest(tenantMappingRule, null);
    } catch (final Exception e) {
      logger.error("Error creating tenant mapping rule", e);
      return managementIdentityTransformer.toMigrationStatusUpdateRequest(tenantMappingRule, e);
    }
  }

  private void assignMappingToTenant(final String tenantId, final String mappingId) {
    try {
      final var request = new TenantMemberRequest(tenantId, mappingId, EntityType.MAPPING);
      tenantServices.addMember(request).join();
    } catch (final Exception e) {
      if (!isConflictError(e)) {
        throw e;
      }
    }
  }
}
