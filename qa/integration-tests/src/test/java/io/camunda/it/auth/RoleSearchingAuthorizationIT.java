/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.protocol.rest.ResourceTypeEnum.AUTHORIZATION;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.protocol.rest.PermissionTypeEnum;
import io.camunda.client.protocol.rest.ResourceTypeEnum;
import io.camunda.it.utils.BrokerITInvocationProvider;
import io.camunda.it.utils.CamundaClientTestFactory.Authenticated;
import io.camunda.it.utils.CamundaClientTestFactory.Permissions;
import io.camunda.it.utils.CamundaClientTestFactory.User;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;

@TestInstance(Lifecycle.PER_CLASS)
class RoleSearchingAuthorizationIT {

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private static final String ADMIN = "admin";
  private static final String RESTRICTED = "restricted-user";
  private static final String RESTRICTED_WITH_READ = "restricted-user-2";
  private static final String DEFAULT_PASSWORD = "password";
  private static final String ROLE_SEARCH_ENDPOINT = "v2/roles/search";
  private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(15);

  private static final User ADMIN_USER =
      new User(
          ADMIN,
          DEFAULT_PASSWORD,
          List.of(
              new Permissions(ResourceTypeEnum.ROLE, PermissionTypeEnum.CREATE, List.of("*")),
              new Permissions(ResourceTypeEnum.ROLE, PermissionTypeEnum.UPDATE, List.of("*")),
              new Permissions(ResourceTypeEnum.ROLE, PermissionTypeEnum.READ, List.of("*")),
              new Permissions(AUTHORIZATION, PermissionTypeEnum.UPDATE, List.of("*"))));

  private static final User RESTRICTED_USER = new User(RESTRICTED, DEFAULT_PASSWORD, List.of());

  private static final User RESTRICTED_USER_WITH_READ_PERMISSION =
      new User(
          RESTRICTED_WITH_READ,
          DEFAULT_PASSWORD,
          List.of(new Permissions(ResourceTypeEnum.ROLE, PermissionTypeEnum.READ, List.of("*"))));

  @RegisterExtension
  static final BrokerITInvocationProvider PROVIDER =
      new BrokerITInvocationProvider()
          .withoutRdbmsExporter()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withUsers(ADMIN_USER, RESTRICTED_USER, RESTRICTED_USER_WITH_READ_PERMISSION);

  @AutoClose private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  private boolean initialized;

  @BeforeEach
  void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    if (!initialized) {
      createRole(adminClient, "role2");
      createRole(adminClient, "role3");
      final int expectedCount = 3; // Admin, role2, role3
      waitForRolesToBeCreated(
          adminClient.getConfiguration().getRestAddress().toString(), ADMIN, expectedCount);
      initialized = true;
    }
  }

  private static RoleSearchResponse searchRoles(final String restAddress, final String username)
      throws URISyntaxException, IOException, InterruptedException {
    final var encodedCredentials =
        Base64.getEncoder()
            .encodeToString("%s:%s".formatted(username, DEFAULT_PASSWORD).getBytes());
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(new URI("%s%s".formatted(restAddress, ROLE_SEARCH_ENDPOINT)))
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .header("Authorization", "Basic %s".formatted(encodedCredentials))
            .build();

    final HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

    return OBJECT_MAPPER.readValue(response.body(), RoleSearchResponse.class);
  }

  @TestTemplate
  void searchShouldReturnAuthorizedRoles(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) throws Exception {
    final var roleSearchResponse =
        searchRoles(
            camundaClient.getConfiguration().getRestAddress().toString(), RESTRICTED_WITH_READ);

    assertThat(roleSearchResponse.items())
        .hasSize(3)
        .map(RoleResponse::name)
        .containsExactlyInAnyOrder("Admin", "role2", "role3");
  }

  @TestTemplate
  void searchShouldReturnEmptyListWhenUnauthorized(
      @Authenticated(RESTRICTED) final CamundaClient camundaClient) throws Exception {
    final var roleSearchResponse =
        searchRoles(camundaClient.getConfiguration().getRestAddress().toString(), RESTRICTED);

    assertThat(roleSearchResponse.items()).hasSize(0).map(RoleResponse::name).isEmpty();
  }

  private static void createRole(final CamundaClient adminClient, final String roleName) {
    adminClient.newCreateRoleCommand().name(roleName).send().join();
  }

  private void waitForRolesToBeCreated(
      final String restAddress, final String name, final int expectedCount) {
    Awaitility.await("should create roles and import in ES")
        .atMost(AWAIT_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var roleSearchResponse = searchRoles(restAddress, name);
              assertThat(roleSearchResponse.items()).hasSize(expectedCount);
            });
  }

  private record RoleSearchResponse(List<RoleResponse> items) {}

  private record RoleResponse(String name) {}
}
