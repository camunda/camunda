/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.OwnerType.APPLICATION;
import static io.camunda.client.api.search.enums.OwnerType.USER;
import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.CREATE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.DELETE;
import static io.camunda.client.api.search.enums.PermissionType.READ;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.AUTHORIZATION;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.CreateAuthorizationResponse;
import io.camunda.client.api.response.DeleteAuthorizationResponse;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.User;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class AuthorizationSearchIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private static final String ADMIN = "admin";
  private static final String RESTRICTED = "restrictedUser";
  private static final String DEFAULT_PASSWORD = "password";
  private static final String AUTH_SEARCH_ENDPOINT = "v2/authorizations/search";

  @UserDefinition
  private static final User ADMIN_USER =
      new User(
          ADMIN,
          DEFAULT_PASSWORD,
          List.of(
              new Permissions(AUTHORIZATION, READ, List.of("*")),
              new Permissions(AUTHORIZATION, CREATE, List.of("*")),
              new Permissions(AUTHORIZATION, DELETE, List.of("*"))));

  @UserDefinition
  private static final User RESTRICTED_USER = new User(RESTRICTED, DEFAULT_PASSWORD, List.of());

  @AutoClose private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  @Test
  void searchShouldReturnAuthorizations(@Authenticated(ADMIN) final CamundaClient adminClient)
      throws Exception {
    final var response =
        searchAuthorizations(adminClient.getConfiguration().getRestAddress().toString(), ADMIN);
    assertThat(response.items()).isNotEmpty();
  }

  @Test
  void searchShouldReturnEmptyListForRestrictedUser(
      @Authenticated(RESTRICTED) final CamundaClient client) throws Exception {
    final var response =
        searchAuthorizations(client.getConfiguration().getRestAddress().toString(), RESTRICTED);
    assertThat(response.items()).isEmpty();
  }

  @Test
  void shouldBeAbleToQueryAuthorizationAfterAdding(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // given
    final var resourceId = "test-resource-" + UUID.randomUUID();

    adminClient
        .newCreateAuthorizationCommand()
        .ownerId(ADMIN)
        .ownerType(USER)
        .resourceId(resourceId)
        .resourceType(PROCESS_DEFINITION)
        .permissionTypes(CREATE_PROCESS_INSTANCE)
        .send()
        .join()
        .getAuthorizationKey();

    // then
    Awaitility.await()
        .untilAsserted(
            () ->
                assertThat(
                        searchAuthorizations(
                                adminClient.getConfiguration().getRestAddress().toString(), ADMIN)
                            .items())
                    .filteredOn(
                        auth ->
                            auth.resourceId().equals(resourceId)
                                && auth.resourceType().equals(PROCESS_DEFINITION)
                                && auth.ownerId().equals(ADMIN))
                    .isEmpty());
  }

  @Test
  void shouldNotShowAuthorizationAfterRemoval(@Authenticated(ADMIN) final CamundaClient adminClient)
      throws Exception {
    // Given
    final String resourceId = "test-resource-" + UUID.randomUUID();
    final CreateAuthorizationResponse createResponse =
        adminClient
            .newCreateAuthorizationCommand()
            .ownerId(ADMIN)
            .ownerType(APPLICATION)
            .resourceId(resourceId)
            .resourceType(PROCESS_DEFINITION)
            .permissionTypes(READ_PROCESS_DEFINITION, CREATE_PROCESS_INSTANCE)
            .send()
            .join();

    // Verify it was created
    Awaitility.await()
        .untilAsserted(
            () ->
                assertThat(
                        searchAuthorizations(
                                adminClient.getConfiguration().getRestAddress().toString(), ADMIN)
                            .items())
                    .anyMatch(
                        auth ->
                            auth.resourceId().equals(resourceId)
                                && auth.resourceType().equals(PROCESS_DEFINITION)
                                && auth.ownerId().equals(ADMIN)));

    // When
    final DeleteAuthorizationResponse deleteResponse =
        adminClient
            .newDeleteAuthorizationCommand(createResponse.getAuthorizationKey())
            .send()
            .join();

    // then
    Awaitility.await()
        .untilAsserted(
            () ->
                assertThat(
                        searchAuthorizations(
                                adminClient.getConfiguration().getRestAddress().toString(), ADMIN)
                            .items())
                    .noneMatch(
                        auth ->
                            auth.resourceId().equals(resourceId)
                                && auth.resourceType().equals(PROCESS_DEFINITION)
                                && auth.ownerId().equals(ADMIN)));
  }

  // TODO once available, this test should use the client to make the request
  private static AuthorizationSearchResponse searchAuthorizations(
      final String restAddress, final String username)
      throws URISyntaxException, IOException, InterruptedException {
    final var encodedCredentials =
        Base64.getEncoder()
            .encodeToString("%s:%s".formatted(username, DEFAULT_PASSWORD).getBytes());
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(new URI("%s%s".formatted(restAddress, AUTH_SEARCH_ENDPOINT)))
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .header("Authorization", "Basic %s".formatted(encodedCredentials))
            .build();

    final HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    return OBJECT_MAPPER.readValue(response.body(), AuthorizationSearchResponse.class);
  }

  private record AuthorizationSearchResponse(List<AuthorizationResponse> items) {}

  private record AuthorizationResponse(
      String ownerId,
      OwnerType ownerType,
      ResourceType resourceType,
      String resourceId,
      List<PermissionType> permissionTypes,
      String authorizationKey) {}
}
