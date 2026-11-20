/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.security.configuration.InitializationConfiguration.DEFAULT_USER_PASSWORD;
import static io.camunda.security.configuration.InitializationConfiguration.DEFAULT_USER_USERNAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.configuration.ConfiguredGroup;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Verifies that the unauthenticated initial admin setup endpoint only works while the admin role
 * has no members at all. The admin role is managed through a group here, so it has no direct user
 * member.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
// role member search, which the setup endpoint relies on, is not implemented for RDBMS on 8.8
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
class InitialAdminSetupIT {

  private static final String ADMIN_GROUP_ID = "admin-group";

  @MultiDbTestApplication
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthenticatedAccess()
          .withSecurityConfig(
              conf -> {
                conf.getInitialization()
                    .setGroups(
                        List.of(
                            new ConfiguredGroup(
                                ADMIN_GROUP_ID,
                                ADMIN_GROUP_ID,
                                "",
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of())));
                conf.getInitialization()
                    .setDefaultRoles(Map.of("admin", Map.of("groups", List.of(ADMIN_GROUP_ID))));
              });

  @AutoClose private final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  void shouldRejectInitialAdminSetupWhenAdminRoleHasOnlyGroupMember() throws Exception {
    // given
    final var username = "setup-" + UUID.randomUUID();
    final var body =
        """
        {"username": "%s", "password": "password", "name": "Setup", "email": "setup@example.com"}
        """
            .formatted(username);

    // when an anonymous caller tries to bootstrap an admin user
    final var response =
        httpClient.send(
            HttpRequest.newBuilder()
                .uri(restUri("/v2/setup/user"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(body))
                .build(),
            BodyHandlers.ofString());

    // then
    assertThat(response.statusCode()).as(response.body()).isEqualTo(403);
    final var userLookup =
        httpClient.send(
            HttpRequest.newBuilder()
                .uri(restUri("/v2/users/" + username))
                .header("Authorization", demoAuthorization())
                .GET()
                .build(),
            BodyHandlers.ofString());
    assertThat(userLookup.statusCode()).as(userLookup.body()).isEqualTo(404);
  }

  private static URI restUri(final String path) {
    return URI.create(BROKER.restAddress().toString().replaceAll("/$", "") + path);
  }

  private static String demoAuthorization() {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString((DEFAULT_USER_USERNAME + ":" + DEFAULT_USER_PASSWORD).getBytes());
  }
}
