/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.User;
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
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class BasicAuthenticationIT {

  public static final String PATH_V2_AUTHENTICATION_ME = "v2/authentication/me";

  @MultiDbTestApplication
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthenticatedAccess();

  private static final String USERNAME = "correct_username";
  private static final String PASSWORD = "correct_password";
  @UserDefinition private static final User USER = new User(USERNAME, PASSWORD, List.of());
  private static CamundaClient camundaClient;
  @AutoClose private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  @Test
  void basicAuthWithValidCredentials(@Authenticated(USERNAME) final CamundaClient userClient)
      throws Exception {
    // when
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(createUri(camundaClient, PATH_V2_AUTHENTICATION_ME))
            .header("Authorization", basicAuthentication(USERNAME))
            .build();
    final HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
  }

  @Test
  void basicAuthWithNoCredentials() throws Exception {
    // when
    final HttpRequest request =
        HttpRequest.newBuilder().uri(createUri(camundaClient, PATH_V2_AUTHENTICATION_ME)).build();

    final HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
  }

  @Test
  void basicAuthWithBadCredentials() throws Exception {
    // when
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(createUri(camundaClient, PATH_V2_AUTHENTICATION_ME))
            .header("Authorization", basicAuthentication("bad"))
            .build();
    final HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
  }

  private static String basicAuthentication(final String user) {
    return "Basic " + Base64.getEncoder().encodeToString((user + ":" + PASSWORD).getBytes());
  }

  private static URI createUri(final CamundaClient client, final String path)
      throws URISyntaxException {
    return new URI("%s%s".formatted(client.getConfiguration().getRestAddress(), path));
  }
}
