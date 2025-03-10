/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.protocol.rest.ResourceTypeEnum.AUTHORIZATION;
import static io.camunda.client.protocol.rest.ResourceTypeEnum.USER;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.protocol.rest.PermissionTypeEnum;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.User;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("multi-db-test")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class UserSearchingAuthorizationIT {

  public static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  @RegisterExtension
  static final CamundaMultiDBExtension EXTENSION = new CamundaMultiDBExtension(BROKER);

  private static final String ADMIN = "admin";
  private static final String RESTRICTED = "restrictedUser";
  private static final String RESTRICTED_WITH_READ = "restrictedUser2";
  private static final String DEFAULT_PASSWORD = "password";
  private static final String USER_SEARCH_ENDPOINT = "v2/users/search";
  private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(15);

  @UserDefinition
  private static final User ADMIN_USER =
      new User(
          ADMIN,
          DEFAULT_PASSWORD,
          List.of(
              new Permissions(USER, PermissionTypeEnum.CREATE, List.of("*")),
              new Permissions(USER, PermissionTypeEnum.UPDATE, List.of("*")),
              new Permissions(USER, PermissionTypeEnum.READ, List.of("*")),
              new Permissions(AUTHORIZATION, PermissionTypeEnum.UPDATE, List.of("*"))));

  @UserDefinition
  private static final User RESTRICTED_USER = new User(RESTRICTED, DEFAULT_PASSWORD, List.of());

  @UserDefinition
  private static final User RESTRICTED_USER_WITH_READ_PERMISSION =
      new User(
          RESTRICTED_WITH_READ,
          DEFAULT_PASSWORD,
          List.of(new Permissions(USER, PermissionTypeEnum.READ, List.of("*"))));

  @AutoClose private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    createUser(adminClient, "user1");
    createUser(adminClient, "user2");
    final var expectedCount = 4; // demo, admin, user1, user2
    waitForUsersToBeCreated(
        adminClient.getConfiguration().getRestAddress().toString(), ADMIN, expectedCount);
  }

  private static UserSearchResponse searchUsers(final String restAddress, final String username)
      throws URISyntaxException, IOException, InterruptedException {
    final var encodedCredentials =
        Base64.getEncoder()
            .encodeToString("%s:%s".formatted(username, DEFAULT_PASSWORD).getBytes());
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(new URI("%s%s".formatted(restAddress, USER_SEARCH_ENDPOINT)))
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .header("Authorization", "Basic %s".formatted(encodedCredentials))
            .build();

    final HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

    return OBJECT_MAPPER.readValue(response.body(), UserSearchResponse.class);
  }

  @Test
  void searchShouldReturnAuthorizedUsers(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient userClient) throws Exception {
    // when
    final var userSearchResponse =
        searchUsers(
            userClient.getConfiguration().getRestAddress().toString(), RESTRICTED_WITH_READ);

    // then
    assertThat(userSearchResponse.items())
        .map(UserResponse::username)
        .contains("demo", "admin", "user1", "user2", "restrictedUser2");
  }

  @Test
  void searchShouldReturnOnlyRestrictedUserWhenUnauthorized(
      @Authenticated(RESTRICTED) final CamundaClient userClient) throws Exception {
    // when
    final var tenantSearchResponse =
        searchUsers(userClient.getConfiguration().getRestAddress().toString(), RESTRICTED);

    // then
    assertThat(tenantSearchResponse.items())
        .hasSize(1)
        .map(UserResponse::username)
        .containsExactlyInAnyOrder("restrictedUser");
  }

  private static void createUser(final CamundaClient adminClient, final String username) {
    adminClient
        .newUserCreateCommand()
        .username(username)
        .email(username + "@example.com")
        .password("password")
        .name(UUID.randomUUID().toString())
        .send()
        .join();
  }

  private static void waitForUsersToBeCreated(
      final String restAddress, final String username, final int expectedCount) {
    Awaitility.await("should create users and import in ES")
        .atMost(AWAIT_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var userSearchResponse = searchUsers(restAddress, username);

              assert userSearchResponse.items().size() == expectedCount;
            });
  }

  private record UserSearchResponse(List<UserResponse> items) {}

  private record UserResponse(String username) {}
}
