/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.sm;

import static io.camunda.migration.identity.MigrationUtil.normalizeID;
import static io.camunda.migration.identity.config.EntitiesProperties.EntityType.MAPPING_RULE;
import static io.camunda.migration.identity.config.EntitiesProperties.EntityType.ROLE;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.config.EntitiesProperties;
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
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class MappingRuleMigrationHandler extends MigrationHandler<MappingRule> {

  private final ManagementIdentityClient managementIdentityClient;
  private final MappingRuleServices mappingRuleServices;
  private final RoleServices roleServices;
  private final TenantServices tenantServices;
  private final EntitiesProperties entitiesProperties;

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
    entitiesProperties = migrationProperties.getEntities();
  }

  @Override
  protected List<MappingRule> fetchBatch(final int page) {
    return List.of();
  }

  @Override
  protected void process(final List<MappingRule> batch) {
    record ClaimKey(String name, String value) {}
    final var mappingRuleByUniqueClaim =
        managementIdentityClient.fetchMappingRules().stream()
            .collect(
                Collectors.groupingBy(rule -> new ClaimKey(rule.claimName(), rule.claimValue())));
    totalMappingRuleCount.addAndGet(mappingRuleByUniqueClaim.size());

    mappingRuleByUniqueClaim
        .values()
        .forEach(
            mappingRulesWithSameClaim -> {
              // Sort group by mapping rule name/id for deterministic selection of the first rule
              mappingRulesWithSameClaim.sort(Comparator.comparing(MappingRule::name));
              final var firstRule = mappingRulesWithSameClaim.getFirst();
              final var mappingRuleId =
                  normalizeID(firstRule.name(), entitiesProperties, MAPPING_RULE);
              final var mappingRuleDTO =
                  new MappingRuleDTO(
                      firstRule.claimName(),
                      firstRule.claimValue(),
                      firstRule.name(),
                      mappingRuleId);
              try {
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
              // Assign all roles and tenants from all mappingRulesWithSameClaim members
              mappingRulesWithSameClaim.forEach(
                  rule -> assignRolesToMappingRule(rule.appliedRoles(), mappingRuleDTO));
              mappingRulesWithSameClaim.forEach(
                  rule -> assignTenantsToMappingRule(rule.appliedTenants(), mappingRuleDTO));
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

  private void assignRolesToMappingRule(
      final Set<Role> appliedRoles, final MappingRuleDTO mappingRule) {
    appliedRoles.forEach(
        role -> {
          final var roleId = normalizeID(role.name(), entitiesProperties, ROLE);
          final var mappingRuleId = mappingRule.mappingRuleId();
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
            if (isConflictError(e)) {
              logger.debug(
                  "Role '{}' already assigned to mapping rule '{}', skipping assignment.",
                  roleId,
                  mappingRuleId);
            } else if (isNotFoundError(e)) {
              // This likely indicates a conflicting mapping rule ID as the role must exist given
              // the Role Migration is required to run before this handler
              // see https://github.com/camunda/camunda/issues/38405
              throw new MigrationException(
                  createConflictingMappingRuleIdMessage(mappingRule, roleId, "role"), e);
            } else {
              throw new MigrationException(
                  "Failed to assign role: " + roleId + " to mapping rule: " + mappingRuleId, e);
            }
          }
          totalRoleAssignmentAttempts.incrementAndGet();
        });
  }

  private void assignTenantsToMappingRule(
      final Set<Tenant> appliedTenants, final MappingRuleDTO mappingRule) {
    final var mappingRuleId = mappingRule.mappingRuleId();
    appliedTenants.forEach(
        tenant -> {
          final var tenantId = tenant.tenantId();
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
            } else if (isNotFoundError(e)) {
              // This likely indicates a conflicting mapping rule ID as the tenant must exist given
              // the Tenant Migration is required to run before this handler.
              // see https://github.com/camunda/camunda/issues/38405
              throw new MigrationException(
                  createConflictingMappingRuleIdMessage(mappingRule, tenantId, "tenant"), e);
            } else {
              logger.debug(
                  "Tenant '{}' already assigned to mapping rule '{}', skipping assignment.",
                  tenantId,
                  mappingRuleId);
            }
          }
          totalTenantAssignmentAttempts.incrementAndGet();
        });
  }

  private static String createConflictingMappingRuleIdMessage(
      final MappingRuleDTO mappingRule, final String entityId, final String entityType) {
    return """
        Failed to assign %s with ID: "%s" to mapping rule with ID: "%s" as the mapping rule could not be found.
        This is likely caused by an inconsistency between the mapping rule state in Management Identity and the Orchestration Cluster.
        Please check whether there is an existing mapping rule with the claimName: "%s" and claimValue: "%s" but a different id than "%s" \
        present the Orchestration Cluster, either setup statically via configuration or created manually.
        It needs to get deleted manually and if required recreated with the same ID: "%s" before re-running the migration to establish a consistent state.
        """
        .formatted(
            entityType,
            entityId,
            mappingRule.mappingRuleId(),
            mappingRule.claimName(),
            mappingRule.claimValue(),
            mappingRule.mappingRuleId(),
            mappingRule.mappingRuleId());
  }
}
