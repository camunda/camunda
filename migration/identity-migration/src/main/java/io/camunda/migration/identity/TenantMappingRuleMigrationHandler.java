/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import io.camunda.migration.identity.dto.TenantMappingRule;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.migration.identity.midentity.ManagementIdentityTransformer;
import io.camunda.migration.identity.midentity.MigrationStatusUpdateRequest;
import io.camunda.zeebe.client.ZeebeClient;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TenantMappingRuleMigrationHandler implements MigrationHandler {

  public static final int SIZE = 100;
  private final ManagementIdentityClient managementIdentityClient;
  private final ManagementIdentityTransformer managementIdentityTransformer;
  private final ZeebeClient zeebeClient;

  public TenantMappingRuleMigrationHandler(
      final ManagementIdentityClient managementIdentityClient,
      final ManagementIdentityTransformer managementIdentityTransformer,
      final ZeebeClient zeebeClient) {
    this.managementIdentityClient = managementIdentityClient;
    this.managementIdentityTransformer = managementIdentityTransformer;
    this.zeebeClient = zeebeClient;
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
      // OPERATOR should be added
      // Name should be added
      zeebeClient
          .newCreateMappingCommand()
          .claimName(tenantMappingRule.getClaimName())
          .claimValue(tenantMappingRule.getClaimValue());
    } catch (final Exception e) {
      if (!isConflictError(e)) {
        return managementIdentityTransformer.toMigrationStatusUpdateRequest(tenantMappingRule, e);
      }
    }

    try {
      // Command for add member
    } catch (final Exception e) {
      if (!isConflictError(e)) {
        return managementIdentityTransformer.toMigrationStatusUpdateRequest(tenantMappingRule, e);
      }
    }

    return managementIdentityTransformer.toMigrationStatusUpdateRequest(tenantMappingRule, null);
  }
}
