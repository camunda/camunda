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
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("multi-db-test")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class AuthorizationSearchIT {

  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  @RegisterExtension
  static final CamundaMultiDBExtension EXTENSION = new CamundaMultiDBExtension(BROKER);

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
          List.of(new Permissions(AUTHORIZATION, PermissionTypeEnum.READ, List.of("*"))));

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

  private record AuthorizationResponse(String resourceId) {}
}
