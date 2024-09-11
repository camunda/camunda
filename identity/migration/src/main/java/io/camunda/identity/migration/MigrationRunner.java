/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.migration;

import static java.util.Arrays.asList;

import io.camunda.service.AuthorizationServices;
import io.camunda.service.UserServices;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Profile("identity-migration")
public class MigrationRunner implements ApplicationRunner {

  final UserServices userService;

  final AuthorizationServices<AuthorizationRecord> authorizationServices;

  final AuthorizationMigrationHandler authorizationMigrationHandler;

  final RestTemplate restTemplate;

  public MigrationRunner(
      final UserServices<UserRecord> userService,
      final AuthorizationServices<AuthorizationRecord> authorizationServices) {
    this.userService = userService;
    this.authorizationServices = authorizationServices;
    this.authorizationMigrationHandler = authorizationMigrationHandler;
    restTemplate = new RestTemplate();
  }

  @Override
  public void run(final ApplicationArguments args) throws Exception {

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
}
