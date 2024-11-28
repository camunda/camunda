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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@EnableConfigurationProperties(IdentityMigrationProperties.class)
@Component("identity-migrator")
public class MigrationRunner implements Migrator {

  private static final Logger LOGGER = LoggerFactory.getLogger(MigrationRunner.class);

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
      migrate();
    }
  }

  private void migrate() {
    while (true) {
      try {
        tenantMigrationHandler.migrate();
        authorizationMigrationHandler.migrate();
        break;
      } catch (final Exception e) {
        LOGGER.warn("Migration failed, let's retry!", e);
        try {
          Thread.sleep(1000);
        } catch (final InterruptedException ex) {
          throw new RuntimeException(ex);
        }
      }
    }
  }

  @Override
  public void acceptArguments(final ApplicationArguments args) {
    this.args = args;
  }

  public static void main(final String[] args) {
    SpringApplication.run(MigrationRunner.class, args);
  }
}
