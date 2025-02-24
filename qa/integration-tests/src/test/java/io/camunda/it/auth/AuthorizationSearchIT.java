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
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthorizationSearchIT {

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private static final String ADMIN = "admin";
  private static final String RESTRICTED = "restrictedUser";
  private static final String DEFAULT_PASSWORD = "password";
  private static final String AUTH_SEARCH_ENDPOINT = "v2/authorizations/search";

  private static final User ADMIN_USER =
      new User(
          ADMIN,
          DEFAULT_PASSWORD,
          List.of(new Permissions(AUTHORIZATION, PermissionTypeEnum.READ, List.of("*"))));

  private static final User RESTRICTED_USER = new User(RESTRICTED, DEFAULT_PASSWORD, List.of());

  @RegisterExtension
  static final BrokerITInvocationProvider PROVIDER =
      new BrokerITInvocationProvider()
          .withoutRdbmsExporter()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withUsers(ADMIN_USER, RESTRICTED_USER);

  @AutoClose private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  @TestTemplate
  void searchShouldReturnAuthorizations(@Authenticated(ADMIN) final CamundaClient adminClient)
      throws Exception {
    final var response =
        searchAuthorizations(adminClient.getConfiguration().getRestAddress().toString(), ADMIN);
    assertThat(response.items()).isNotEmpty();
  }

  @TestTemplate
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
