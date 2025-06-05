/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.READ;
import static io.camunda.client.api.search.enums.ResourceType.MAPPING_RULE;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.MappingDefinition;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestMapping;
import io.camunda.qa.util.auth.TestUser;
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
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class MappingAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private static final String ADMIN = "admin";
  private static final String RESTRICTED = "restrictedUser";
  private static final String UNAUTHORIZED = "unauthorizedUser";
  private static final String DEFAULT_PASSWORD = "password";
  private static final String MAPPING_SEARCH_ENDPOINT = "v2/mapping-rules/search";

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN,
          DEFAULT_PASSWORD,
          List.of(
              new Permissions(MAPPING_RULE, CREATE, List.of("*")),
              new Permissions(MAPPING_RULE, READ, List.of("*"))));

  @UserDefinition
  private static final TestUser RESTRICTED_USER =
      new TestUser(
          RESTRICTED, DEFAULT_PASSWORD, List.of(new Permissions(MAPPING_RULE, READ, List.of("*"))));

  @UserDefinition
  private static final TestUser UNAUTHORIZED_USER =
      new TestUser(UNAUTHORIZED, DEFAULT_PASSWORD, List.of());

  @MappingDefinition
  private static final TestMapping MAPPING_1 =
      new TestMapping("mapping1", "test-name", "test-value");

  @MappingDefinition
  private static final TestMapping MAPPING_2 =
      new TestMapping("mapping2", "test-name2", "test-value2");

  @AutoClose private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  @Test
  void searchShouldReturnAuthorizedMappings(
      @Authenticated(RESTRICTED) final CamundaClient userClient) throws Exception {
    final var mappingSearchResponse =
        searchMappings(userClient.getConfiguration().getRestAddress().toString(), RESTRICTED);

    assertThat(mappingSearchResponse.items())
        .hasSizeGreaterThanOrEqualTo(2)
        .map(MappingResponse::name)
        .contains("mapping1", "mapping2");
  }

  @Test
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
        .mappingId(mappingId)
        .name(mappingId)
        .send()
        .join();
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
