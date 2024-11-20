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
import io.camunda.service.AuthorizationServices;
import io.camunda.service.UserServices;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

@Component("identity-migrator")
public class MigrationRunner implements Migrator {

  private ApplicationArguments args;
  private final UserServices userService;
  private final AuthorizationServices authorizationServices;
  private final AuthorizationMigrationHandler authorizationMigrationHandler;

  public MigrationRunner(
      final UserServices userService,
      final AuthorizationServices authorizationServices,
      final AuthorizationMigrationHandler authorizationMigrationHandler) {
    this.userService = userService;
    this.authorizationServices = authorizationServices;
    this.authorizationMigrationHandler = authorizationMigrationHandler;
  }

  @Override
  public void run() {

    final String command =
        args.containsOption("command") ? args.getOptionValues("command").getFirst() : "migrate";
    if (!asList("migrate", "status").contains(command)) {
      if ("migrate".equals(command)) {
        authorizationMigrationHandler.migrate();
      }
      throw new IllegalArgumentException("Unknown command: " + command);
    }

    // TODO: place holder to logic
    System.out.println("Migration Logic");
  }

  @Override
  public void acceptArguments(final ApplicationArguments args) {
    this.args = args;
  }
}
