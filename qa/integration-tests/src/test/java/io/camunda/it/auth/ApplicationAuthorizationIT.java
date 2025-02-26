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

import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.it.util.AuthorizationsUtil;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.message.BasicHeader;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@ZeebeIntegration
@TestInstance(Lifecycle.PER_CLASS)
class ApplicationAuthorizationIT {

  private static final String CONTEXT_PATH = "/camunda";
  private static final String BASE_URL = "http://localhost:8080" + CONTEXT_PATH;
  private static final String RESTRICTED = "restricted-user";
  private static final String ADMIN = "admin";

  @ZeebeIntegration.TestZeebe(initMethod = "initTestStandaloneCamunda")
  private static TestStandaloneCamunda testInstance;

  @SuppressWarnings("unused")
  static void initTestStandaloneCamunda() {
    testInstance =
        new TestStandaloneCamunda()
            .withProperty("spring.profiles.active", "consolidated-auth,identity,broker,operate")
            .withProperty("server.port", "8080")
            .withProperty("server.servlet.context-path", CONTEXT_PATH)
            .withSecurityConfig(c -> c.getAuthorizations().setEnabled(true))
            .withAuthenticationMethod(AuthenticationMethod.BASIC);
  }

  @BeforeAll
  static void setUp() throws Exception {
    final var authorizationsUtil =
        AuthorizationsUtil.create(testInstance, testInstance.getElasticSearchHostAddress());

    authorizationsUtil.createUserWithPermissions(
        ADMIN, "password", new AuthorizationsUtil.Permissions(APPLICATION, ACCESS, List.of("*")));

    authorizationsUtil.createUserWithPermissions(
        RESTRICTED,
        "password",
        new AuthorizationsUtil.Permissions(APPLICATION, ACCESS, List.of("tasklist")));
  }

  @Test
  void accessAppUserWithoutAppAccessNotAllowed() throws ProtocolException, IOException {
    try (final CloseableHttpClient httpClient =
        HttpClients.custom().disableRedirectHandling().build()) {
      final HttpGet request = new HttpGet(BASE_URL + "/operate/user");
      request.addHeader(basicAuthentication(RESTRICTED));
      try (final CloseableHttpResponse response = httpClient.execute(request)) {
        assertRedirectToForbidden(response);
      }
    }
  }

  @Test
  void accessAppNoUserAllowed() throws ProtocolException, IOException {
    try (final CloseableHttpClient httpClient =
        HttpClients.custom().disableRedirectHandling().build()) {
      final HttpGet request = new HttpGet(BASE_URL + "/operate/user");
      try (final CloseableHttpResponse response = httpClient.execute(request)) {
        assertNoRedirectToForbidden(response);
      }
    }
  }

  @Test
  void accessApiUserWithoutAppAccessAllowed() throws ProtocolException, IOException {
    try (final CloseableHttpClient httpClient =
        HttpClients.custom().disableRedirectHandling().build()) {
      final HttpGet request = new HttpGet(BASE_URL + "/api/user?name=operate");
      request.addHeader(basicAuthentication(RESTRICTED));
      try (final CloseableHttpResponse response = httpClient.execute(request)) {
        assertNoRedirectToForbidden(response);
      }
    }
  }

  @Test
  void accessStaticUserWithoutAppAccessAllowed() throws ProtocolException, IOException {
    try (final CloseableHttpClient httpClient =
        HttpClients.custom().disableRedirectHandling().build()) {
      final HttpGet request = new HttpGet(BASE_URL + "/operate/image.svg");
      request.addHeader(basicAuthentication(RESTRICTED));
      try (final CloseableHttpResponse response = httpClient.execute(request)) {
        assertNoRedirectToForbidden(response);
      }
    }
  }

  @Test
  void accessAppUserWithSpecificAppAccessAllowed() throws ProtocolException, IOException {
    try (final CloseableHttpClient httpClient =
        HttpClients.custom().disableRedirectHandling().build()) {
      final HttpGet request = new HttpGet(BASE_URL + "/tasklist/user");
      request.addHeader(basicAuthentication(RESTRICTED));
      try (final CloseableHttpResponse response = httpClient.execute(request)) {
        assertNoRedirectToForbidden(response);
      }
    }
  }

  @Test
  void accessAppUserWithAppWildcardAccessAllowed() throws ProtocolException, IOException {
    try (final CloseableHttpClient httpClient =
        HttpClients.custom().disableRedirectHandling().build()) {
      final HttpGet request = new HttpGet(BASE_URL + "/operate/users");
      request.addHeader(basicAuthentication(ADMIN));
      try (final CloseableHttpResponse response = httpClient.execute(request)) {
        assertNoRedirectToForbidden(response);
      }
    }
  }

  private static void assertRedirectToForbidden(final CloseableHttpResponse response)
      throws ProtocolException {
    final int statusCode = response.getCode();
    Assertions.assertEquals(HttpStatus.SC_MOVED_TEMPORARILY, statusCode);
    final Header locationHeader = response.getHeader("Location");
    Assertions.assertNotNull(locationHeader);
    Assertions.assertEquals(BASE_URL + "/operate/forbidden", locationHeader.getValue());
  }

  private static void assertNoRedirectToForbidden(final CloseableHttpResponse response)
      throws ProtocolException {
    final int statusCode = response.getCode();
    Assertions.assertNotEquals(HttpStatus.SC_MOVED_TEMPORARILY, statusCode);
    final Header locationHeader = response.getHeader("Location");
    Assertions.assertNull(locationHeader);
  }

  private static @NotNull BasicHeader basicAuthentication(final String restricted) {
    return new BasicHeader(
        "Authorization",
        "Basic " + Base64.getEncoder().encodeToString((restricted + ":" + "password").getBytes()));
  }
}
