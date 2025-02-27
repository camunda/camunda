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

import io.camunda.client.CamundaClient;
import io.camunda.it.utils.CamundaMultiDBExtension;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.User;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestSimpleCamundaApplication;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("multi-db-test")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
class ApplicationAuthorizationIT {
  static final TestSimpleCamundaApplication STANDALONE_CAMUNDA =
      new TestSimpleCamundaApplication().withBasicAuth().withAuthorizationsEnabled()
          .withProperty("spring.profiles.active", "consolidated-auth,identity,broker,operate");
  @RegisterExtension
  static final CamundaMultiDBExtension EXTENSION = new CamundaMultiDBExtension(STANDALONE_CAMUNDA);
  private static final String RESTRICTED = "restricted-user";
  private static final String ADMIN = "admin";
  private static final String DEFAULT_PASSWORD = "password";
  @UserDefinition
  private static final User ADMIN_USER =
      new User(
          ADMIN,
          DEFAULT_PASSWORD,
          List.of(new Permissions(APPLICATION, ACCESS, List.of("*"))));

  @UserDefinition
  private static final User RESTRICTED_USER = new User(RESTRICTED, DEFAULT_PASSWORD, List.of(new Permissions(APPLICATION, ACCESS, List.of("tasklist"))));

  @AutoClose
  private static final CloseableHttpClient HTTP_CLIENT = HttpClients.custom().disableRedirectHandling().build();

  @Test
  void accessAppUserWithoutAppAccessNotAllowed(@Authenticated(RESTRICTED) final CamundaClient restrictedClient) throws IOException, ProtocolException {
      final HttpGet request = new HttpGet(restrictedClient.getConfiguration().getRestAddress() + "operate/user");
      request.addHeader(basicAuthentication(RESTRICTED));
      try (final CloseableHttpResponse response = HTTP_CLIENT.execute(request)) {
        assertRedirectToForbidden(response);
      }
  }

  @Test
  void accessAppNoUserAllowed(@Authenticated(ADMIN) final CamundaClient client) throws IOException {
      final HttpGet request = new HttpGet(client.getConfiguration().getRestAddress() + "operate/user");
      try (final CloseableHttpResponse response = HTTP_CLIENT.execute(request)) {
        assertThat(response.getCode()).isEqualTo(HttpStatus.SC_OK);
      }
  }

  @Test
  void accessApiUserWithoutAppAccessAllowed(@Authenticated(RESTRICTED) final CamundaClient restrictedClient) throws IOException {
      final HttpGet request = new HttpGet(restrictedClient.getConfiguration().getRestAddress() + "v2/topology");
      request.addHeader(basicAuthentication(RESTRICTED));
      try (final CloseableHttpResponse response = HTTP_CLIENT.execute(request)) {
        assertThat(response.getCode()).isEqualTo(HttpStatus.SC_OK);
      }
  }

  @Test
  void accessStaticUserWithoutAppAccessAllowed(@Authenticated(RESTRICTED) final CamundaClient restrictedClient) throws IOException {
      final HttpGet request = new HttpGet(restrictedClient.getConfiguration().getRestAddress() + "operate/image.svg");
      request.addHeader(basicAuthentication(RESTRICTED));
      try (final CloseableHttpResponse response = HTTP_CLIENT.execute(request)) {
        // we expect not found here as frontend resources are not packaged for integration tests
        assertThat(response.getCode()).isEqualTo(HttpStatus.SC_NOT_FOUND);
    }
  }

  @Test
  void accessAppUserWithSpecificAppAccessAllowed(@Authenticated(RESTRICTED) final CamundaClient restrictedClient) throws IOException {
      final HttpGet request = new HttpGet(restrictedClient.getConfiguration().getRestAddress() + "tasklist/user");
      request.addHeader(basicAuthentication(RESTRICTED));
      try (final CloseableHttpResponse response = HTTP_CLIENT.execute(request)) {
        assertThat(response.getCode()).isEqualTo(HttpStatus.SC_OK);
    }
  }

  @Test
  void accessAppUserWithAppWildcardAccessAllowed(@Authenticated(ADMIN) final CamundaClient adminClient) throws IOException {
      final HttpGet request = new HttpGet(adminClient.getConfiguration().getRestAddress() + "operate/users");
      request.addHeader(basicAuthentication(ADMIN));
      try (final CloseableHttpResponse response = HTTP_CLIENT.execute(request)) {
        assertThat(response.getCode()).isEqualTo(HttpStatus.SC_OK);
    }
  }

  private static void assertRedirectToForbidden(final CloseableHttpResponse response)
      throws ProtocolException {
    assertThat(response.getCode()).isEqualTo(HttpStatus.SC_MOVED_TEMPORARILY);
    assertThat(response.getHeader("Location"))
        .isNotNull()
        .extracting(NameValuePair::getValue)
        .satisfies(location -> assertThat(location).endsWith("/operate/forbidden"));
  }

  private static BasicHeader basicAuthentication(final String restricted) {
    return new BasicHeader(
        "Authorization",
        "Basic " + Base64.getEncoder().encodeToString((restricted + ":" + "password").getBytes()));
  }
}
