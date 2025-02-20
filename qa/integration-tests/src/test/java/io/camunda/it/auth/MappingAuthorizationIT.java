/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.protocol.rest.PermissionTypeEnum.CREATE;
import static io.camunda.client.protocol.rest.PermissionTypeEnum.READ;
import static io.camunda.client.protocol.rest.ResourceTypeEnum.MAPPING_RULE;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
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
class MappingAuthorizationIT {

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private static final String ADMIN = "admin";
  private static final String RESTRICTED = "restricted-user";
  private static final String UNAUTHORIZED = "unauthorized-user";
  private static final String DEFAULT_PASSWORD = "password";
  private static final String MAPPING_SEARCH_ENDPOINT = "v2/mapping-rules/search";
  private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(15);

  private static final User ADMIN_USER =
      new User(
          ADMIN,
          DEFAULT_PASSWORD,
          List.of(
              new Permissions(MAPPING_RULE, CREATE, List.of("*")),
              new Permissions(MAPPING_RULE, READ, List.of("*"))));

  private static final User RESTRICTED_USER =
      new User(
          RESTRICTED, DEFAULT_PASSWORD, List.of(new Permissions(MAPPING_RULE, READ, List.of("*"))));

  private static final User UNAUTHORIZED_USER = new User(UNAUTHORIZED, DEFAULT_PASSWORD, List.of());

  @RegisterExtension
  static final BrokerITInvocationProvider PROVIDER =
      new BrokerITInvocationProvider()
          .withoutRdbmsExporter()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withUsers(ADMIN_USER, RESTRICTED_USER, UNAUTHORIZED_USER);

  @AutoClose private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  private boolean initialized;

  @BeforeEach
  void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    if (!initialized) {
      createMapping(adminClient, "test-name", "test-value", "mapping1");
      createMapping(adminClient, "test-name2", "test-value2", "mapping2");
      final int expectedCount = 2;
      waitForMappingsToBeCreated(
          adminClient.getConfiguration().getRestAddress().toString(), ADMIN, expectedCount);
      initialized = true;
    }
  }

  @TestTemplate
  void searchShouldReturnAuthorizedMappings(
      @Authenticated(RESTRICTED) final CamundaClient userClient) throws Exception {
    final var mappingSearchResponse =
        searchMappings(userClient.getConfiguration().getRestAddress().toString(), RESTRICTED);

    assertThat(mappingSearchResponse.items())
        .hasSize(2)
        .map(MappingResponse::name)
        .containsExactlyInAnyOrder("mapping1", "mapping2");
  }

  @TestTemplate
  void searchShouldReturnEmptyListWhenUnauthorized(
      @Authenticated(UNAUTHORIZED) final CamundaClient userClient) throws Exception {
    final var mappingSearchResponse =
        searchMappings(userClient.getConfiguration().getRestAddress().toString(), UNAUTHORIZED);

    assertThat(mappingSearchResponse.items()).isEmpty();
  }

  private static void createMapping(
      final CamundaClient adminClient,
      final String name,
      final String value,
      final String mappingId) {
    adminClient
        .newCreateMappingCommand()
        .claimName(name)
        .claimValue(value)
        .id(mappingId)
        .name(mappingId)
        .send()
        .join();
  }

  private void waitForMappingsToBeCreated(
      final String restAddress, final String username, final int expectedCount) {
    Awaitility.await("should create mappings and import in ES")
        .atMost(AWAIT_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var mappingSearchResponse = searchMappings(restAddress, username);
              assertThat(mappingSearchResponse.items()).hasSize(expectedCount);
            });
  }

  // TODO once available, this test should use the client to make the request
  private static MappingSearchResponse searchMappings(
      final String restAddress, final String username)
      throws URISyntaxException, IOException, InterruptedException {
    final var encodedCredentials =
        Base64.getEncoder()
            .encodeToString("%s:%s".formatted(username, DEFAULT_PASSWORD).getBytes());
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(new URI("%s%s".formatted(restAddress, MAPPING_SEARCH_ENDPOINT)))
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .header("Authorization", "Basic %s".formatted(encodedCredentials))
            .build();

    final HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    return OBJECT_MAPPER.readValue(response.body(), MappingSearchResponse.class);
  }

  private record MappingSearchResponse(List<MappingResponse> items) {}

  private record MappingResponse(String name) {}
}
