/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.csrf;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.client.CamundaClient;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.configuration.CsrfConfiguration;
import io.camunda.security.configuration.InitializationConfiguration;
import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class CsrfTokenIT {

  @MultiDbTestApplication
  private static final TestCamundaApplication CAMUNDA_APPLICATION =
      new TestCamundaApplication()
          .withAuthenticatedAccess()
          .withSecurityConfig(
              sc -> {
                final var csrfConfig = new CsrfConfiguration();
                csrfConfig.setEnabled(true);
                sc.setCsrf(csrfConfig);
              })
          .withAdditionalProfile("consolidated-auth");

  private static CamundaClient camundaClient;

  @Test
  public void visitProtectedEndpointSuccessfulWhenCsrfTokenPresent()
      throws URISyntaxException, IOException, InterruptedException {
    final var operateClient = CAMUNDA_APPLICATION.newOperateClient();
    final var uri = operateClient.getEndpoint();

    final var cookieHandler = new CookieManager();
    final var httpClient = HttpClient.newBuilder().cookieHandler(cookieHandler).build();
    final var csrfToken = loginWithCamundaService(httpClient, uri);

    assertThat(cookieHandler.getCookieStore().getCookies().stream().map(HttpCookie::getName))
        .hasSize(2)
        .contains(WebSecurityConfig.X_CSRF_TOKEN, WebSecurityConfig.SESSION_COOKIE);

    final var cookieSessionId =
        cookieHandler.getCookieStore().getCookies().stream()
            .filter(c -> WebSecurityConfig.SESSION_COOKIE.equals(c.getName()))
            .findFirst()
            .get();
    final var cookieCsrfToken =
        cookieHandler.getCookieStore().getCookies().stream()
            .filter(c -> WebSecurityConfig.X_CSRF_TOKEN.equals(c.getName()))
            .findFirst()
            .get();
    final var httpClientWithoutCookie = HttpClient.newBuilder().build();
    final var response =
        httpClientWithoutCookie.send(
            HttpRequest.newBuilder()
                .uri(new URI(uri.toString() + "api/processes/grouped"))
                .header(HttpHeaders.COOKIE, cookieSessionId.toString())
                .header(HttpHeaders.COOKIE, cookieCsrfToken.toString())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(WebSecurityConfig.X_CSRF_TOKEN, csrfToken)
                .POST(BodyPublishers.ofString("{}"))
                .build(),
            BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
  }

  @Test
  public void visitProtectedEndpointNotAuthorizedWhenCsrfTokenAbsent()
      throws URISyntaxException, IOException, InterruptedException {
    final var operateClient = CAMUNDA_APPLICATION.newOperateClient();
    final var uri = operateClient.getEndpoint();

    final var cookieHandler = new CookieManager();
    final var httpClient = HttpClient.newBuilder().cookieHandler(cookieHandler).build();
    loginWithCamundaService(httpClient, uri);

    assertThat(cookieHandler.getCookieStore().getCookies().stream().map(HttpCookie::getName))
        .hasSize(2)
        .contains(WebSecurityConfig.X_CSRF_TOKEN, WebSecurityConfig.SESSION_COOKIE);

    final var cookieSessionId =
        cookieHandler.getCookieStore().getCookies().stream()
            .filter(c -> WebSecurityConfig.SESSION_COOKIE.equals(c.getName()))
            .findFirst()
            .get();
    final var cookieCsrfToken =
        cookieHandler.getCookieStore().getCookies().stream()
            .filter(c -> WebSecurityConfig.X_CSRF_TOKEN.equals(c.getName()))
            .findFirst()
            .get();
    final var httpClientWithoutCookie = HttpClient.newBuilder().build();
    final var response =
        httpClientWithoutCookie.send(
            HttpRequest.newBuilder()
                .uri(new URI(uri.toString() + "api/processes/grouped"))
                .header(HttpHeaders.COOKIE, cookieSessionId.toString())
                .header(HttpHeaders.COOKIE, cookieCsrfToken.toString())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(BodyPublishers.ofString("{}"))
                .build(),
            BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
  }

  @Test
  public void visitPublicEndpointNotAuthorizedWhenCsrfTokenAbsent()
      throws URISyntaxException, IOException, InterruptedException {
    final var operateClient = CAMUNDA_APPLICATION.newOperateClient();
    final var uri = operateClient.getEndpoint();

    final var cookieHandler = new CookieManager();
    final var httpClient = HttpClient.newBuilder().cookieHandler(cookieHandler).build();
    loginWithCamundaService(httpClient, uri);

    assertThat(cookieHandler.getCookieStore().getCookies().stream().map(HttpCookie::getName))
        .hasSize(2)
        .contains(WebSecurityConfig.X_CSRF_TOKEN, WebSecurityConfig.SESSION_COOKIE);

    final var cookieSessionId =
        cookieHandler.getCookieStore().getCookies().stream()
            .filter(c -> WebSecurityConfig.SESSION_COOKIE.equals(c.getName()))
            .findFirst()
            .get();
    final var httpClientWithoutCookie = HttpClient.newBuilder().build();
    final var response =
        httpClientWithoutCookie.send(
            HttpRequest.newBuilder()
                .uri(new URI(uri.toString() + "v1/process-definitions/search"))
                .header(HttpHeaders.COOKIE, cookieSessionId.toString())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(BodyPublishers.ofString("{}"))
                .build(),
            BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
  }

  @Test
  public void webappsCanShareSameCsrfToken()
      throws URISyntaxException, IOException, InterruptedException {
    final var tasklistClient = CAMUNDA_APPLICATION.newTasklistClient();
    final var tasklistUri = tasklistClient.getEndpoint();

    final var operateClient = CAMUNDA_APPLICATION.newOperateClient();
    final var operateUri = operateClient.getEndpoint();

    final var cookieHandler = new CookieManager();
    final var httpClient = HttpClient.newBuilder().cookieHandler(cookieHandler).build();
    final var csrfToken = loginWithCamundaService(httpClient, tasklistUri);

    assertThat(cookieHandler.getCookieStore().getCookies().stream().map(HttpCookie::getName))
        .hasSize(2)
        .contains(WebSecurityConfig.X_CSRF_TOKEN, WebSecurityConfig.SESSION_COOKIE);

    final var cookieSessionId =
        cookieHandler.getCookieStore().getCookies().stream()
            .filter(c -> WebSecurityConfig.SESSION_COOKIE.equals(c.getName()))
            .findFirst()
            .get();
    final var cookieCsrfToken =
        cookieHandler.getCookieStore().getCookies().stream()
            .filter(c -> WebSecurityConfig.X_CSRF_TOKEN.equals(c.getName()))
            .findFirst()
            .get();
    final var httpClientWithoutCookie = HttpClient.newBuilder().build();
    final var response =
        httpClientWithoutCookie.send(
            HttpRequest.newBuilder()
                .uri(new URI(operateUri.toString() + "api/processes/grouped"))
                .header(HttpHeaders.COOKIE, cookieSessionId.toString())
                .header(HttpHeaders.COOKIE, cookieCsrfToken.toString())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(WebSecurityConfig.X_CSRF_TOKEN, csrfToken)
                .POST(BodyPublishers.ofString("{}"))
                .build(),
            BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
  }

  private String loginWithCamundaService(final HttpClient httpClient, final URI uri)
      throws IOException, InterruptedException, URISyntaxException {

    final var authRequest =
        HttpRequest.newBuilder()
            .uri(new URI(uri.toString() + "login"))
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED.toString())
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "username="
                        + InitializationConfiguration.DEFAULT_USER_USERNAME
                        + "&password="
                        + InitializationConfiguration.DEFAULT_USER_PASSWORD))
            .build();
    httpClient.send(authRequest, BodyHandlers.ofString());
    final var rootServletRequest =
        httpClient.send(
            HttpRequest.newBuilder().uri(new URI(uri.toString())).GET().build(),
            BodyHandlers.ofString());

    return rootServletRequest.headers().firstValue(WebSecurityConfig.X_CSRF_TOKEN).orElse(null);
  }
}
