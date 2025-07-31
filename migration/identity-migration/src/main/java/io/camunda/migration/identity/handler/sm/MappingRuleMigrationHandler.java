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
import io.camunda.migration.identity.config.IdentityMigrationProperties;
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

  private final AtomicInteger createdMappingRuleCount = new AtomicInteger();
  private final AtomicInteger totalMappingRuleCount = new AtomicInteger();
  private final AtomicInteger assignedRoleCount = new AtomicInteger();
  private final AtomicInteger assignedTenantCount = new AtomicInteger();
  private final AtomicInteger totalRoleAssignmentAttempts = new AtomicInteger();
  private final AtomicInteger totalTenantAssignmentAttempts = new AtomicInteger();

  public MappingRuleMigrationHandler(
      final ManagementIdentityClient managementIdentityClient,
      final MappingRuleServices mappingRuleServices,
      final RoleServices roleServices,
      final TenantServices tenantServices,
      final CamundaAuthentication camundaAuthentication,
      final IdentityMigrationProperties migrationProperties) {
    super(migrationProperties.getBackpressureDelay());
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
    final var mappingRules = managementIdentityClient.fetchMappingRules();
    totalMappingRuleCount.addAndGet(mappingRules.size());

    mappingRules.forEach(
        mappingRule -> {
          final var mappingRuleId = normalizeID(mappingRule.name());
          try {
            final var mappingRuleDTO =
                new MappingRuleDTO(
                    mappingRule.claimName(),
                    mappingRule.claimValue(),
                    mappingRule.name(),
                    mappingRuleId);
            retryOnBackpressure(
                () -> mappingRuleServices.createMappingRule(mappingRuleDTO).join(),
                "Failed to create mapping rule with ID: " + mappingRuleId);
            createdMappingRuleCount.incrementAndGet();
          } catch (final Exception e) {
            if (!isConflictError(e)) {
              throw new MigrationException(
                  "Failed to migrate mapping rule with ID: " + mappingRuleId, e);
            }
            logger.debug(
                "Mapping rule with ID '{}' already exists, skipping creation.", mappingRuleId);
          }
          assignRolesToMappingRule(mappingRule.appliedRoles(), mappingRuleId);
          assignTenantsToMappingRule(mappingRule.appliedTenants(), mappingRuleId);
        });
  }

  @Override
  protected void logSummary() {
    logger.info(
        "Mapping rule migration completed: Created {} out of {} mapping rules, the remaining existed already. Assigned {} roles out of {} attempted, the remaining were already assigned. Assigned {} tenants out of {} attempted, the remaining were already assigned.",
        createdMappingRuleCount.get(),
        totalMappingRuleCount.get(),
        assignedRoleCount.get(),
        totalRoleAssignmentAttempts.get(),
        assignedTenantCount.get(),
        totalTenantAssignmentAttempts.get());
  }

  private void assignRolesToMappingRule(final Set<Role> appliedRoles, final String mappingRuleId) {
    appliedRoles.forEach(
        role -> {
          final var roleId = normalizeID(role.name());
          try {
            final var roleMember =
                new RoleMemberRequest(roleId, mappingRuleId, EntityType.MAPPING_RULE);
            retryOnBackpressure(
                () -> roleServices.addMember(roleMember).join(),
                "Failed to assign role with ID: "
                    + roleId
                    + " to mapping rule with ID: "
                    + mappingRuleId);
            assignedRoleCount.incrementAndGet();
          } catch (final Exception e) {
            if (!isConflictError(e)) {
              throw new MigrationException(
                  "Failed to assign role: " + roleId + " to mapping rule: " + mappingRuleId, e);
            }
            logger.debug(
                "Role '{}' already assigned to mapping rule '{}', skipping assignment.",
                roleId,
                mappingRuleId);
          }
          totalRoleAssignmentAttempts.incrementAndGet();
        });
  }

  private void assignTenantsToMappingRule(
      final Set<Tenant> appliedTenants, final String mappingRuleId) {
    appliedTenants.forEach(
        tenant -> {
          final var tenantId = normalizeID(tenant.tenantId());
          try {
            final var tenantMember =
                new TenantMemberRequest(tenantId, mappingRuleId, EntityType.MAPPING_RULE);
            retryOnBackpressure(
                () -> tenantServices.addMember(tenantMember).join(),
                "Failed to assign tenant with ID: "
                    + tenantId
                    + " to mapping rule with ID: "
                    + mappingRuleId);
            assignedTenantCount.incrementAndGet();
          } catch (final Exception e) {
            if (!isConflictError(e)) {
              throw new MigrationException(
                  "Failed to assign tenant: " + tenantId + " to mapping rule: " + mappingRuleId, e);
            }
            logger.debug(
                "Tenant '{}' already assigned to mapping rule '{}', skipping assignment.",
                tenantId,
                mappingRuleId);
          }
          totalTenantAssignmentAttempts.incrementAndGet();
        });
  }
}
