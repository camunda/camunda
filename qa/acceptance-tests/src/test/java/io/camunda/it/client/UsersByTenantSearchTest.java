/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.client.api.search.response.TenantUser;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AutoClose;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
public class UsersByTenantSearchTest {

  private static final String BASE_PATH = "v2/tenants";
  private static CamundaClient camundaClient;

  private static final String USER_USERNAME_1 = "Alice" + Strings.newRandomValidUsername();
  private static final String USER_USERNAME_2 = "Bob" + Strings.newRandomValidUsername();
  private static final String USER_USERNAME_3 = Strings.newRandomValidUsername();
  private static final String TENANT_ID = Strings.newRandomValidIdentityId();
  private static final String ADMIN_USER_NAME = "foo";
  private static final String USER_USERNAME_2 = "testUser1";
  private static final String USER_USERNAME_3 = "testUser2";
  private static final String SEARCH_USERNAME = "searchUser";
  private static final String TEST_PASSWORD = "p455w0rd";
  @AutoClose private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN_USER_NAME,
          TEST_PASSWORD,
          List.of(
              new Permissions(ResourceType.AUTHORIZATION, PermissionType.CREATE, List.of("*")),
              new Permissions(ResourceType.AUTHORIZATION, PermissionType.UPDATE, List.of("*")),
              new Permissions(ResourceType.USER, PermissionType.CREATE, List.of("*")),
              new Permissions(ResourceType.TENANT, PermissionType.CREATE, List.of("*")),
              new Permissions(ResourceType.TENANT, PermissionType.UPDATE, List.of("*"))));

  @UserDefinition
  private static final TestUser TEST_USER2 =
      new TestUser(USER_USERNAME_2, TEST_PASSWORD, List.of());

  @UserDefinition
  private static final TestUser TEST_USER3 =
      new TestUser(USER_USERNAME_3, TEST_PASSWORD, List.of());

  @UserDefinition
  private static final TestUser SEARCH_USER =
      new TestUser(
          SEARCH_USERNAME,
          TEST_PASSWORD,
          List.of(
              new Permissions(ResourceType.TENANT, PermissionType.READ, List.of("*")),
              new Permissions(ResourceType.TENANT, PermissionType.UPDATE, List.of("*")),
              new Permissions(ResourceType.APPLICATION, PermissionType.ACCESS, List.of("*")),
              new Permissions(ResourceType.RESOURCE, PermissionType.READ, List.of("*"))));

  @BeforeAll
  static void setup(
      @Authenticated(ADMIN_USER_NAME) final CamundaClient adminClient,
      @Authenticated(USER_USERNAME_2) final CamundaClient user2Client,
      @Authenticated(USER_USERNAME_3) final CamundaClient user3Client) {

    createTenant(adminClient, TENANT_ID);
    assignUserToTenant(adminClient, ADMIN_USER_NAME, TENANT_ID);
    assignUserToTenant(adminClient, USER_USERNAME_2, TENANT_ID);
    assignUserToTenant(adminClient, USER_USERNAME_3, TENANT_ID);

    waitUntilExactUsersExist(
        adminClient, ADMIN_USER_NAME, TENANT_ID, ADMIN_USER_NAME, USER_USERNAME_2, USER_USERNAME_3);
  }

  @Test
  void shouldReturnUsersByTenant(@Authenticated(SEARCH_USERNAME) final CamundaClient camundaClient)
      throws URISyntaxException, IOException, InterruptedException {
    assignUserToTenant(camundaClient, SEARCH_USERNAME, TENANT_ID);
    final var users = searchForUsersByTenant(camundaClient, TENANT_ID, ADMIN_USER_NAME);

    assertThat(users.statusCode()).isEqualTo(200);
    assertThat(users.body()).contains(ADMIN_USER_NAME, USER_USERNAME_2, USER_USERNAME_3);
  }

  //  @Test
  //  @Disabled
  //  void shouldRejectIfMissingTenantId(
  //      @Authenticated(ADMIN_USER_NAME) final CamundaClient camundaClient) {
  //    // when / then
  //    assertThatThrownBy(() -> searchForUsersByTenant(camundaClient, null, ADMIN_USER_NAME))
  //        .isInstanceOf(IllegalArgumentException.class)
  //        .hasMessageContaining("tenantId must not be null");
  //  }

  //  @Test
  //  @Disabled
  //  void shouldRejectIfEmptyTenantId() {
  //    // when / then
  //    assertThatThrownBy(() -> searchForUsersByTenant(camundaClient, "", ADMIN_USER_NAME))
  //        .isInstanceOf(IllegalArgumentException.class)
  //        .hasMessageContaining("tenantId must not be empty");
  //  }

  private static void createTenant(final CamundaClient adminClient, final String tenantId) {
    adminClient.newCreateTenantCommand().tenantId(tenantId).name("name").send().join();
  @Test
  void shouldRejectIfMissingTenantId() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newUsersByTenantSearchRequest(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be null");
  }

  private static void assignUserToTenant(
      final CamundaClient adminClient, final String username, final String tenantId) {
    adminClient.newAssignUserToTenantCommand().username(username).tenantId(tenantId).send().join();
  @Test
  void shouldRejectIfEmptyTenantId() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newUsersByTenantSearchRequest("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be empty");
  }

  private static HttpResponse<String> searchForUsersByTenant(
      final CamundaClient client, final String tenantId, final String username)
      throws URISyntaxException, IOException, InterruptedException {
    final String url =
        client.getConfiguration().getRestAddress() + BASE_PATH + "/" + tenantId + "/users/search";

    final var encodedCredentials =
        Base64.getEncoder().encodeToString("%s:%s".formatted(username, TEST_PASSWORD).getBytes());
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(new URI(url))
            .POST(HttpRequest.BodyPublishers.noBody())
            .header("Authorization", "Basic %s".formatted(encodedCredentials))
            .header("Content-Type", "application/json")
            .build();
    // Send the request and get the response
    return HTTP_CLIENT.send(request, BodyHandlers.ofString());
  }

  private static void waitUntilExactUsersExist(
      final CamundaClient camundaClient,
      final String clientUsername,
      final String tenantId,
      final String... usernames) {
    await("should wait until the expected users are in the secondary storage.")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              // we're passing the username of the searching user explicitly here. Once a client
              // command is available, this information will be taken from the client
              final var result = searchForUsersByTenant(camundaClient, tenantId, clientUsername);
              assertThat(result.statusCode()).isEqualTo(200);
              assertThat(result.body()).contains(usernames);
            });
  }
}
