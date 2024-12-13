/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import static java.util.Arrays.asList;

import com.google.common.util.concurrent.Uninterruptibles;
import io.camunda.migration.api.Migrator;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@EnableConfigurationProperties(IdentityMigrationProperties.class)
@Component("identity-migrator")
public class MigrationRunner implements Migrator {

  private static final Logger LOGGER = LoggerFactory.getLogger(MigrationRunner.class);

  private ApplicationArguments args;

  private final AuthorizationMigrationHandler authorizationMigrationHandler;
  private final TenantMigrationHandler tenantMigrationHandler;
  private final TenantMappingRuleMigrationHandler tenantMappingRuleMigrationHandler;
  private final UserTenantsMigrationHandler userTenantsMigrationHandler;

  public MigrationRunner(
      final AuthorizationMigrationHandler authorizationMigrationHandler,
      final TenantMigrationHandler tenantMigrationHandler,
      final TenantMappingRuleMigrationHandler tenantMappingRuleMigrationHandler,
      final UserTenantsMigrationHandler userTenantsMigrationHandler) {
    this.authorizationMigrationHandler = authorizationMigrationHandler;
    this.tenantMigrationHandler = tenantMigrationHandler;
    this.tenantMappingRuleMigrationHandler = tenantMappingRuleMigrationHandler;
    this.userTenantsMigrationHandler = userTenantsMigrationHandler;
  }

  @Override
  public void run() {

    final String command =
        args.containsOption("command") ? args.getOptionValues("command").getFirst() : "migrate";
    if (!asList("migrate", "status").contains(command)) {
      throw new IllegalArgumentException("Unknown command: " + command);
    }

    if ("migrate".equals(command)) {
      migrate();
    }
  }

  private void migrate() {
    while (true) {
      try {
        tenantMigrationHandler.migrate();
        tenantMappingRuleMigrationHandler.migrate();
        userTenantsMigrationHandler.migrate();
        authorizationMigrationHandler.migrate();
        break;
      } catch (final Exception e) {
        LOGGER.warn("Migration failed, let's retry!", e);
        Uninterruptibles.sleepUninterruptibly(1000, TimeUnit.MILLISECONDS);
      }
    }
  }

  @Override
  public void acceptArguments(final ApplicationArguments args) {
    this.args = args;
  }
}
