/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import com.google.common.util.concurrent.Uninterruptibles;
import io.camunda.migration.api.Migrator;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@EnableConfigurationProperties(IdentityMigrationProperties.class)
@Component("identity-migrator")
final class MigrationRunner implements Migrator {

  private static final Logger LOGGER = LoggerFactory.getLogger(MigrationRunner.class);

  private final AuthorizationMigrationHandler authorizationMigrationHandler;
  private final TenantMigrationHandler tenantMigrationHandler;
  private final TenantMappingRuleMigrationHandler tenantMappingRuleMigrationHandler;
  private final UserTenantsMigrationHandler userTenantsMigrationHandler;
  private final GroupTenantsMigrationHandler groupTenantsMigrationHandler;

  public MigrationRunner(
      final AuthorizationMigrationHandler authorizationMigrationHandler,
      final TenantMigrationHandler tenantMigrationHandler,
      final TenantMappingRuleMigrationHandler tenantMappingRuleMigrationHandler,
      final UserTenantsMigrationHandler userTenantsMigrationHandler,
      final GroupTenantsMigrationHandler groupTenantsMigrationHandler) {
    this.authorizationMigrationHandler = authorizationMigrationHandler;
    this.tenantMigrationHandler = tenantMigrationHandler;
    this.tenantMappingRuleMigrationHandler = tenantMappingRuleMigrationHandler;
    this.userTenantsMigrationHandler = userTenantsMigrationHandler;
    this.groupTenantsMigrationHandler = groupTenantsMigrationHandler;
  }

  @Override
  public Void call() {
    migrate();
    return null;
  }

  private void migrate() {
    while (true) {
      try {
        tenantMigrationHandler.migrate();
        tenantMappingRuleMigrationHandler.migrate();
        userTenantsMigrationHandler.migrate();
        authorizationMigrationHandler.migrate();
        groupTenantsMigrationHandler.migrate();
        break;
      } catch (final Exception e) {
        LOGGER.warn("Migration failed, let's retry!", e);
        Uninterruptibles.sleepUninterruptibly(1000, TimeUnit.MILLISECONDS);
      }
    }
  }
}
