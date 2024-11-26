/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import static java.util.Arrays.asList;

import io.camunda.migration.api.Migrator;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@EnableConfigurationProperties(IdentityMigrationProperties.class)
@Component("identity-migrator")
public class MigrationRunner implements Migrator {

  private ApplicationArguments args;

  private final AuthorizationMigrationHandler authorizationMigrationHandler;
  private final TenantMigrationHandler tenantMigrationHandler;

  public MigrationRunner(
      final AuthorizationMigrationHandler authorizationMigrationHandler,
      final TenantMigrationHandler tenantMigrationHandler) {
    this.authorizationMigrationHandler = authorizationMigrationHandler;
    this.tenantMigrationHandler = tenantMigrationHandler;
  }

  @Override
  public void run() {

    final String command =
        args.containsOption("command") ? args.getOptionValues("command").getFirst() : "migrate";
    if (!asList("migrate", "status").contains(command)) {
      throw new IllegalArgumentException("Unknown command: " + command);
    }

    if ("migrate".equals(command)) {
      tenantMigrationHandler.migrate();
      authorizationMigrationHandler.migrate();
    }
  }

  @Override
  public void acceptArguments(final ApplicationArguments args) {
    this.args = args;
  }
}
