/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.sm;

import static io.camunda.migration.identity.MigrationUtil.normalizeID;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.dto.MappingRule;
import io.camunda.migration.identity.dto.Role;
import io.camunda.migration.identity.dto.Tenant;
import io.camunda.migration.identity.handler.MigrationHandler;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.MappingRuleServices.MappingRuleDTO;
import io.camunda.service.RoleServices;
import io.camunda.service.RoleServices.RoleMemberRequest;
import io.camunda.service.TenantServices;
import io.camunda.service.TenantServices.TenantMemberRequest;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class MappingRuleMigrationHandler extends MigrationHandler<MappingRule> {

  private final ManagementIdentityClient managementIdentityClient;
  private final MappingRuleServices mappingRuleServices;
  private final RoleServices roleServices;
  private final TenantServices tenantServices;

  private final AtomicInteger createdMappingCount = new AtomicInteger();
  private final AtomicInteger totalMappingCount = new AtomicInteger();
  private final AtomicInteger assignedRoleCount = new AtomicInteger();
  private final AtomicInteger assignedTenantCount = new AtomicInteger();
  private final AtomicInteger totalRoleAssignmentAttempts = new AtomicInteger();
  private final AtomicInteger totalTenantAssignmentAttempts = new AtomicInteger();

  public MappingRuleMigrationHandler(
      final ManagementIdentityClient managementIdentityClient,
      final MappingRuleServices mappingRuleServices,
      final RoleServices roleServices,
      final TenantServices tenantServices,
      final CamundaAuthentication camundaAuthentication) {
    this.managementIdentityClient = managementIdentityClient;
    this.mappingRuleServices = mappingRuleServices.withAuthentication(camundaAuthentication);
    this.roleServices = roleServices.withAuthentication(camundaAuthentication);
    this.tenantServices = tenantServices.withAuthentication(camundaAuthentication);
  }

  @Override
  protected List<MappingRule> fetchBatch(final int page) {
    return List.of();
  }

  @Override
  protected void process(final List<MappingRule> batch) {
    final var mappings = managementIdentityClient.fetchMappingRules();
    totalMappingCount.addAndGet(mappings.size());

    mappings.forEach(
        mapping -> {
          final var mappingId = normalizeID(mapping.name());
          try {
            mappingRuleServices.createMappingRule(
                new MappingRuleDTO(
                    mapping.claimName(), mapping.claimValue(), mapping.name(), mappingId));
            createdMappingCount.incrementAndGet();
          } catch (final Exception e) {
            if (!isConflictError(e)) {
              throw new MigrationException("Failed to migrate mapping with ID: " + mappingId, e);
            }
            logger.debug("Mapping with ID '{}' already exists, skipping creation.", mappingId);
          }
          assignRolesToMapping(mapping.appliedRoles(), mappingId);
          assignTenantsToMapping(mapping.appliedTenants(), mappingId);
        });
  }

  @Override
  protected void logSummary() {
    logger.info(
        "Mapping migration completed: Created {} out of {} mapping rules, the remaining existed already. Assigned {} roles out of {} attempted, the remaining were already assigned. Assigned {} tenants out of {} attempted, the remaining were already assigned.",
        createdMappingCount.get(),
        totalMappingCount.get(),
        assignedRoleCount.get(),
        totalRoleAssignmentAttempts.get(),
        assignedTenantCount.get(),
        totalTenantAssignmentAttempts.get());
  }

  private void assignRolesToMapping(final Set<Role> appliedRoles, final String mappingId) {
    appliedRoles.forEach(
        role -> {
          final var roleId = normalizeID(role.name());
          try {
            roleServices.addMember(
                new RoleMemberRequest(roleId, mappingId, EntityType.MAPPING_RULE));
            assignedRoleCount.incrementAndGet();
          } catch (final Exception e) {
            if (!isConflictError(e)) {
              throw new MigrationException(
                  "Failed to assign role: " + roleId + " to mapping: " + mappingId, e);
            }
            logger.debug(
                "Role '{}' already assigned to mapping '{}', skipping assignment.",
                roleId,
                mappingId);
          }
          totalRoleAssignmentAttempts.incrementAndGet();
        });
  }

  private void assignTenantsToMapping(final Set<Tenant> appliedTenants, final String mappingId) {
    appliedTenants.forEach(
        tenant -> {
          final var tenantId = normalizeID(tenant.tenantId());
          try {
            tenantServices.addMember(
                new TenantMemberRequest(tenantId, mappingId, EntityType.MAPPING_RULE));
            assignedTenantCount.incrementAndGet();
          } catch (final Exception e) {
            if (!isConflictError(e)) {
              throw new MigrationException(
                  "Failed to assign tenant: " + tenantId + " to mapping: " + mappingId, e);
            }
            logger.debug(
                "Tenant '{}' already assigned to mapping '{}', skipping assignment.",
                tenantId,
                mappingId);
          }
          totalTenantAssignmentAttempts.incrementAndGet();
        });
  }
}
