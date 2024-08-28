/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.migration;

import io.camunda.service.AuthorizationServices;
import io.camunda.service.UserServices;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class MigrationRunner implements ApplicationRunner {

  final UserServices<UserRecord> userService;

  final AuthorizationServices<AuthorizationRecord> authorizationServices;

  final RestTemplate restTemplate;

  public MigrationRunner(
      final UserServices<UserRecord> userService,
      final AuthorizationServices<AuthorizationRecord> authorizationServices) {
    this.userService = userService;
    this.authorizationServices = authorizationServices;
    restTemplate = new RestTemplate();
  }

  @Override
  public void run(final ApplicationArguments args) throws Exception {
    final String command =
        args.containsOption("command") ? args.getOptionValues("command").getFirst() : "migrate";
    final String mode =
        args.containsOption("mode") ? args.getOptionValues("mode").getFirst() : "resume";
    final String entities =
        args.containsOption("entities") ? args.getOptionValues("entities").getFirst() : null;

    // TODO: place holder to logic
    System.out.println("Migration Logic");
  }
}
