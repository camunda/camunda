/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.protocol.rest.ResourceTypeEnum.AUTHORIZATION;
import static io.camunda.client.protocol.rest.ResourceTypeEnum.GROUP;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.protocol.rest.PermissionTypeEnum;
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
class GroupSearchingAuthorizationIT {

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private static final String ADMIN = "admin";
  private static final String RESTRICTED = "restricted-user";
  private static final String RESTRICTED_WITH_READ = "restricted-user-2";
  private static final String DEFAULT_PASSWORD = "password";
  private static final String GROUP_SEARCH_ENDPOINT = "v2/groups/search";
  private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(15);

  private static final User ADMIN_USER =
      new User(
          ADMIN,
          DEFAULT_PASSWORD,
          List.of(
              new Permissions(GROUP, PermissionTypeEnum.CREATE, List.of("*")),
              new Permissions(GROUP, PermissionTypeEnum.UPDATE, List.of("*")),
              new Permissions(GROUP, PermissionTypeEnum.READ, List.of("*")),
              new Permissions(AUTHORIZATION, PermissionTypeEnum.UPDATE, List.of("*"))));

  private static final User RESTRICTED_USER = new User(RESTRICTED, DEFAULT_PASSWORD, List.of());

  private static final User RESTRICTED_USER_WITH_READ_PERMISSION =
      new User(
          RESTRICTED_WITH_READ,
          DEFAULT_PASSWORD,
          List.of(new Permissions(GROUP, PermissionTypeEnum.READ, List.of("*"))));

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
      createGroup(adminClient, "group1");
      createGroup(adminClient, "group2");
      final int expectedCount = 2;
      waitForGroupsToBeCreated(
          adminClient.getConfiguration().getRestAddress().toString(), ADMIN, expectedCount);
      initialized = true;
    }
  }

  private static GroupSearchResponse searchGroups(final String restAddress, final String username)
      throws URISyntaxException, IOException, InterruptedException {
    final var encodedCredentials =
        Base64.getEncoder()
            .encodeToString("%s:%s".formatted(username, DEFAULT_PASSWORD).getBytes());
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(new URI("%s%s".formatted(restAddress, GROUP_SEARCH_ENDPOINT)))
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .header("Authorization", "Basic %s".formatted(encodedCredentials))
            .build();

    final HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

    return OBJECT_MAPPER.readValue(response.body(), GroupSearchResponse.class);
  }

  @TestTemplate
  void searchShouldReturnAuthorizedGroups(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) throws Exception {
    final var groupSearchResponse =
        searchGroups(
            camundaClient.getConfiguration().getRestAddress().toString(), RESTRICTED_WITH_READ);

    assertThat(groupSearchResponse.items())
        .hasSize(2)
        .map(GroupResponse::name)
        .containsExactlyInAnyOrder("group1", "group2");
  }

  @TestTemplate
  void searchShouldReturnEmptyListWhenUnauthorized(
      @Authenticated(RESTRICTED) final CamundaClient camundaClient) throws Exception {
    final var groupSearchResponse =
        searchGroups(camundaClient.getConfiguration().getRestAddress().toString(), RESTRICTED);

    assertThat(groupSearchResponse.items()).hasSize(0).map(GroupResponse::name).isEmpty();
  }

  private static void createGroup(final CamundaClient adminClient, final String groupName) {
    adminClient.newCreateGroupCommand().name(groupName).send().join();
  }

  private void waitForGroupsToBeCreated(
      final String restAddress, final String username, final int expectedCount) {
    Awaitility.await("should create groups and import in ES")
        .atMost(AWAIT_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var groupSearchResponse = searchGroups(restAddress, username);
              assertThat(groupSearchResponse.items()).hasSize(expectedCount);
            });
  }

  private record GroupSearchResponse(List<GroupResponse> items) {}

  private record GroupResponse(String name) {}
}
