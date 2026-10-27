/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.net.HttpURLConnection;
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
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class MeAuthorizationsDisabledIT {

  private static final String USERNAME = "meAuthorizationsDisabledUser";
  private static final String PASSWORD = "password";
  private static final ObjectMapper JSON = new ObjectMapper();

  @MultiDbTestApplication
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthorizationsDisabled()
          .withAuthenticatedAccess();

  @UserDefinition private static final TestUser USER = new TestUser(USERNAME, PASSWORD, List.of());

  @AutoClose private final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  void shouldIndicateWhenAuthorizationsAreDisabled(
      @Authenticated(USERNAME) final CamundaClient client) throws Exception {
    // when
    final var request =
        HttpRequest.newBuilder()
            .uri(createUri(client, "v2/authentication/me/authorizations/search"))
            .header("Authorization", basicAuthentication())
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
    final var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    final var responseBody = JSON.readTree(response.body());

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    assertThat(responseBody.path("authorizationsEnabled").asBoolean()).isFalse();
    // user creation always grants a self USER authorization, so items is not empty even when
    // authorization checks are disabled
    assertThat(responseBody.path("items"))
        .allSatisfy(
            item -> {
              assertThat(item.path("ownerId").asText()).isEqualTo(USERNAME);
              assertThat(item.path("ownerType").asText()).isEqualTo("USER");
              assertThat(item.path("resourceType").asText()).isEqualTo("USER");
              assertThat(item.path("resourceId").asText()).isEqualTo(USERNAME);
            });
  }

  private static String basicAuthentication() {
    return "Basic " + Base64.getEncoder().encodeToString((USERNAME + ":" + PASSWORD).getBytes());
  }

  private static URI createUri(final CamundaClient client, final String path)
      throws URISyntaxException {
    final String base = client.getConfiguration().getRestAddress().toString();
    final String separator = base.endsWith("/") ? "" : "/";
    return new URI(base + separator + path);
  }
}
