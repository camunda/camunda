/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.protocol.rest.PermissionTypeEnum.ACCESS;
import static io.camunda.client.protocol.rest.ResourceTypeEnum.APPLICATION;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.User;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestSimpleCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class ApplicationAuthorizationIT {

  private static final String PATH_OPERATE = "operate";
  private static final String PATH_OPERATE_WEBAPP_USER = "/user";

  @MultiDbTestApplication
  private static final TestSimpleCamundaApplication STANDALONE_CAMUNDA =
      new TestSimpleCamundaApplication().withBasicAuth().withAuthorizationsEnabled();

  private static final String RESTRICTED = "restricted";
  private static final String ADMIN = "admin";
  private static final String DEFAULT_PASSWORD = "password";

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @UserDefinition
  private static final User ADMIN_USER =
      new User(
          ADMIN, DEFAULT_PASSWORD, List.of(new Permissions(APPLICATION, ACCESS, List.of("*"))));

  @UserDefinition
  private static final User RESTRICTED_USER =
      new User(
          RESTRICTED,
          DEFAULT_PASSWORD,
          List.of(new Permissions(APPLICATION, ACCESS, List.of("tasklist"))));

  @AutoClose
  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder().followRedirects(Redirect.NEVER).build();

  @ParameterizedTest
  @ValueSource(strings = {"operate", "identity"})
  void accessAppUserWithoutAppAccessNotAllowed(
      final String appName, @Authenticated(RESTRICTED) final CamundaClient restrictedClient)
      throws IOException, URISyntaxException, InterruptedException {
    // when
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(createUri(restrictedClient, appName + PATH_OPERATE_WEBAPP_USER))
            .header("Authorization", basicAuthentication(RESTRICTED))
            .build();
    final HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

    // then
    assertRedirectToForbidden(appName, response);
  }

  @ParameterizedTest
  @ValueSource(strings = {"operate", "identity", "tasklist"})
  void accessAppNoUserAllowed(
      final String appName, @Authenticated(ADMIN) final CamundaClient client)
      throws IOException, URISyntaxException, InterruptedException {
    // when
    final HttpRequest request =
        HttpRequest.newBuilder().uri(createUri(client, appName + PATH_OPERATE_WEBAPP_USER)).build();
    final HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

    // then
    assertAccessAllowed(response);
  }

  @Test
  void accessApiUserWithoutAppAccessAllowed(
      @Authenticated(RESTRICTED) final CamundaClient restrictedClient)
      throws IOException, URISyntaxException, InterruptedException {
    // when
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(createUri(restrictedClient, "v2/topology"))
            .header("Authorization", basicAuthentication(RESTRICTED))
            .build();
    final HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

    // then
    assertAccessAllowed(response);
  }

  @Test
  void accessStaticUserWithoutAppAccessAllowed(
      @Authenticated(RESTRICTED) final CamundaClient restrictedClient)
      throws IOException, URISyntaxException, InterruptedException {
    // when
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(createUri(restrictedClient, PATH_OPERATE + "/image.svg"))
            .header("Authorization", basicAuthentication(RESTRICTED))
            .build();
    final HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

    // then
    assertAccessAllowed(response);
  }

  @Test
  void accessAppUserWithSpecificAppAccessAllowed(
      @Authenticated(RESTRICTED) final CamundaClient restrictedClient)
      throws IOException, URISyntaxException, InterruptedException {
    // when
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(createUri(restrictedClient, "tasklist/user"))
            .header("Authorization", basicAuthentication(RESTRICTED))
            .build();
    final HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

    // then
    assertAccessAllowed(response);
  }

  @ParameterizedTest
  @ValueSource(strings = {"operate", "identity", "tasklist"})
  void accessAppUserWithAppWildcardAccessAllowed(
      final String appName, @Authenticated(ADMIN) final CamundaClient adminClient)
      throws IOException, URISyntaxException, InterruptedException {
    // when
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(createUri(adminClient, appName + PATH_OPERATE_WEBAPP_USER))
            .header("Authorization", basicAuthentication(ADMIN))
            .build();
    final HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

    // then
    assertAccessAllowed(response);
  }

  @Test
  void meContainsAppUserSpecificAuthorizations(
      @Authenticated(RESTRICTED) final CamundaClient restrictedClient)
      throws IOException, URISyntaxException, InterruptedException {
    // when
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(createUri(restrictedClient, "v2/authentication/me"))
            .header("Authorization", basicAuthentication(RESTRICTED))
            .build();
    final HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

    // then
    final MeResponse meResponse = OBJECT_MAPPER.readValue(response.body(), MeResponse.class);
    assertThat(meResponse.authorizedApplications).containsExactly("tasklist");
  }

  private static void assertRedirectToForbidden(
      final String appName, final HttpResponse<String> response) {
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_MOVED_TEMP);
    assertThat(response.headers().firstValue("Location"))
        .isPresent()
        .get()
        .satisfies(location -> assertThat(location).endsWith(appName + "/forbidden"));
  }

  private static String basicAuthentication(final String user) {
    return "Basic "
        + Base64.getEncoder().encodeToString((user + ":" + DEFAULT_PASSWORD).getBytes());
  }

  private static URI createUri(final CamundaClient client, final String path)
      throws URISyntaxException {
    return new URI("%s%s".formatted(client.getConfiguration().getRestAddress(), path));
  }

  private static void assertAccessAllowed(final HttpResponse<String> response) {
    assertThat(response.statusCode())
        .isNotIn(
            HttpURLConnection.HTTP_UNAUTHORIZED,
            HttpURLConnection.HTTP_FORBIDDEN,
            HttpURLConnection.HTTP_MOVED_TEMP);
  }

  private record MeResponse(List<String> authorizedApplications) {}
}
