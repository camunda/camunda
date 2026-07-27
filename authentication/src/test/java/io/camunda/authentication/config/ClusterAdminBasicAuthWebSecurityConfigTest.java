/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.authentication.config.controllers.TestApiController;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Tests for the cluster-admin security chain ({@code /cluster/v2/**}) with Basic auth.
 *
 * <p>Runs against {@link TestApiController}'s cluster-admin dummy endpoints (the real {@code
 * DummyClusterTopologyController} lives in another module); the chain is exercised by path, so the
 * behavior is identical.
 */
@SpringBootTest(
    classes = {WebSecurityConfigTestContext.class, WebSecurityConfig.class},
    properties = {
      "camunda.security.authentication.unprotected-api=false",
      "camunda.security.authentication.method=basic",
      "camunda.security.cluster-admin.basic.users[0].name=cluster-operator",
      "camunda.security.cluster-admin.basic.users[0].password=cluster-secret"
    })
public class ClusterAdminBasicAuthWebSecurityConfigTest extends AbstractWebSecurityConfigTest {

  private static final String CLUSTER_ADMIN_USER = "cluster-operator";
  private static final String CLUSTER_ADMIN_PASSWORD = "cluster-secret";

  @Test
  public void shouldReturn200ForProtectedEndpointWithValidClusterAdminCredentials() {
    // when
    final MvcTestResult result =
        mockMvcTester
            .get()
            .headers(basicAuth(CLUSTER_ADMIN_USER, CLUSTER_ADMIN_PASSWORD))
            .uri("https://localhost" + TestApiController.DUMMY_CLUSTER_ADMIN_ENDPOINT)
            .exchange();

    // then
    assertThat(result).hasStatusOk();
  }

  @Test
  public void shouldReturn401ForProtectedEndpointWithoutCredentials() {
    // when
    final MvcTestResult result =
        mockMvcTester
            .get()
            .uri("https://localhost" + TestApiController.DUMMY_CLUSTER_ADMIN_ENDPOINT)
            .exchange();

    // then
    assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  public void shouldReturn401ForProtectedEndpointWithWrongPassword() {
    // when
    final MvcTestResult result =
        mockMvcTester
            .get()
            .headers(basicAuth(CLUSTER_ADMIN_USER, "wrong-password"))
            .uri("https://localhost" + TestApiController.DUMMY_CLUSTER_ADMIN_ENDPOINT)
            .exchange();

    // then
    assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  public void shouldReturn401ForProtectedEndpointWithUnknownUsername() {
    // when
    final MvcTestResult result =
        mockMvcTester
            .get()
            .headers(basicAuth("someone-else", CLUSTER_ADMIN_PASSWORD))
            .uri("https://localhost" + TestApiController.DUMMY_CLUSTER_ADMIN_ENDPOINT)
            .exchange();

    // then
    assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  public void shouldAllowPublicStatusEndpointWithoutCredentials() {
    // when — the carved-out public status endpoint is hit with no credentials
    final MvcTestResult result =
        mockMvcTester
            .get()
            .uri("https://localhost" + TestApiController.DUMMY_CLUSTER_ADMIN_STATUS_ENDPOINT)
            .exchange();

    // then — permitAll lets it through (dummy returns 200; the real endpoint returns 204)
    assertThat(result).hasStatus2xxSuccessful();
  }

  @Test
  public void shouldRejectWrongPasswordOnPublicStatusEndpoint() {
    // when — the public status endpoint is hit with a wrong password
    final MvcTestResult result =
        mockMvcTester
            .get()
            .headers(basicAuth(CLUSTER_ADMIN_USER, "wrong-password"))
            .uri("https://localhost" + TestApiController.DUMMY_CLUSTER_ADMIN_STATUS_ENDPOINT)
            .exchange();

    // then — permitAll only waives a missing credential; a bad one is still rejected by the Basic
    // auth filter before the authorization decision is reached
    assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  public void shouldRequireCredentialsForSiblingOfPublicStatusEndpoint() {
    // when — a sibling of the public path (trailing slash) is hit with no credentials
    final MvcTestResult result =
        mockMvcTester
            .get()
            .uri("https://localhost" + TestApiController.DUMMY_CLUSTER_ADMIN_STATUS_ENDPOINT + "/")
            .exchange();

    // then — only the exact path is public; everything else under /cluster/v2/** stays protected
    assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  public void shouldReturn404ForUnknownClusterAdminPathWithValidCredentials() {
    // when
    final MvcTestResult result =
        mockMvcTester
            .get()
            .headers(basicAuth(CLUSTER_ADMIN_USER, CLUSTER_ADMIN_PASSWORD))
            .uri("https://localhost/cluster/v2/does-not-exist")
            .exchange();

    // then — authenticated, but no handler for the path
    assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  public void shouldNotEstablishSessionForClusterAdminRequest() {
    // when
    final MvcTestResult result =
        mockMvcTester
            .get()
            .headers(basicAuth(CLUSTER_ADMIN_USER, CLUSTER_ADMIN_PASSWORD))
            .uri("https://localhost" + TestApiController.DUMMY_CLUSTER_ADMIN_ENDPOINT)
            .exchange();

    // then — the chain is SessionCreationPolicy.STATELESS, so the request creates no session
    assertThat(result.getRequest().getSession(false))
        .as("cluster-admin Basic auth is stateless and must not establish a session")
        .isNull();
  }

  private static HttpHeaders basicAuth(final String username, final String password) {
    final HttpHeaders headers = new HttpHeaders();
    headers.add(
        HttpHeaders.AUTHORIZATION,
        "Basic "
            + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8)));
    return headers;
  }
}
