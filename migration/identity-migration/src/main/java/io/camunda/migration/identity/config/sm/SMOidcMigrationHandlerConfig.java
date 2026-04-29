/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config.sm;

import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.migration.identity.handler.sm.MappingRuleMigrationHandler;
import io.camunda.migration.identity.handler.sm.RoleMigrationHandler;
import io.camunda.migration.identity.handler.sm.TenantMigrationHandler;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * Wires the migration handlers used in the SM OIDC profile. Each handler can be disabled via its
 * corresponding {@code camunda.migration.identity.handler.oidc.<name>.enabled=false} property.
 * Handlers run in declared order and {@code mapping-rule} consumes both roles created by {@code
 * role} and tenants created by {@code tenant}; disabling either predecessor while keeping {@code
 * mapping-rule} enabled raises a {@link io.camunda.migration.api.MigrationException} at runtime.
 */
@Configuration
@ConditionalOnOidc
public class SMOidcMigrationHandlerConfig {

  public static final String ROLE_ENABLED = "camunda.migration.identity.handler.oidc.role.enabled";
  public static final String TENANT_ENABLED =
      "camunda.migration.identity.handler.oidc.tenant.enabled";
  public static final String MAPPING_RULE_ENABLED =
      "camunda.migration.identity.handler.oidc.mapping-rule.enabled";

  @Bean
  @Order(1)
  @ConditionalOnProperty(name = ROLE_ENABLED, matchIfMissing = true)
  public RoleMigrationHandler roleMigrationHandler(
      final CamundaAuthentication authentication,
      final ManagementIdentityClient managementIdentityClient,
      final RoleServices roleServices,
      final AuthorizationServices authorizationServices,
      final IdentityMigrationProperties migrationProperties) {
    return new RoleMigrationHandler(
        authentication,
        managementIdentityClient,
        roleServices,
        authorizationServices,
        migrationProperties);
  }

  @Bean
  @Order(2)
  @ConditionalOnProperty(name = TENANT_ENABLED, matchIfMissing = true)
  public TenantMigrationHandler tenantMigrationHandler(
      final ManagementIdentityClient managementIdentityClient,
      final TenantServices tenantService,
      final CamundaAuthentication camundaAuthentication,
      final IdentityMigrationProperties migrationProperties) {
    return new TenantMigrationHandler(
        managementIdentityClient, tenantService, camundaAuthentication, migrationProperties);
  }

  @Bean
  @Order(3)
  @ConditionalOnProperty(name = MAPPING_RULE_ENABLED, matchIfMissing = true)
  public MappingRuleMigrationHandler mappingRuleMigrationHandler(
      final ManagementIdentityClient managementIdentityClient,
      final MappingRuleServices mappingRuleServices,
      final RoleServices roleServices,
      final TenantServices tenantServices,
      final CamundaAuthentication camundaAuthentication,
      final IdentityMigrationProperties migrationProperties) {
    return new MappingRuleMigrationHandler(
        managementIdentityClient,
        mappingRuleServices,
        roleServices,
        tenantServices,
        camundaAuthentication,
        migrationProperties);
  }
}
