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
import io.camunda.authentication.config.controllers.TestUserDetailsService;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cross-chain isolation ("leak trap") for the cluster-admin chain: a cluster-admin credential must
 * not authenticate on the regular {@code /v2/**} API, and a DB-backed credential ({@code demo})
 * must not authenticate on {@code /cluster/v2/**}.
 */
@SpringBootTest(
    classes = {WebSecurityConfigTestContext.class, WebSecurityConfig.class},
    properties = {
      "camunda.security.authentication.unprotected-api=false",
      "camunda.security.authentication.method=basic",
      "camunda.security.cluster-admin.basic.users[0].name=cluster-operator",
      "camunda.security.cluster-admin.basic.users[0].password=cluster-secret"
    })
public class ClusterAdminChainIsolationTest extends AbstractWebSecurityConfigTest {

  private static final String CLUSTER_ADMIN_USER = "cluster-operator";
  private static final String CLUSTER_ADMIN_PASSWORD = "cluster-secret";

  @Test
  public void shouldRejectClusterAdminCredentialsOnRegularV2Endpoint() {
    // when — cluster-admin credential presented to the regular /v2 API
    final MvcTestResult result =
        mockMvcTester
            .get()
            .headers(basicAuth(CLUSTER_ADMIN_USER, CLUSTER_ADMIN_PASSWORD))
            .uri("https://localhost" + TestApiController.DUMMY_V2_API_ENDPOINT)
            .exchange();

    // then
    assertThat(result)
        .as("a cluster-admin credential must not authenticate against the regular /v2 API")
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  public void shouldRejectDbBackedUserCredentialsOnClusterAdminEndpoint() {
    // when — the DB-backed demo credential presented to the cluster-admin API
    final MvcTestResult result =
        mockMvcTester
            .get()
            .headers(
                basicAuth(
                    TestUserDetailsService.DEMO_USERNAME, TestUserDetailsService.DEMO_USERNAME))
            .uri("https://localhost" + TestApiController.DUMMY_CLUSTER_ADMIN_ENDPOINT)
            .exchange();

    // then
    assertThat(result)
        .as("a DB-backed user must not authenticate against the cluster-admin API")
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  public void shouldRejectWebappSessionOnClusterAdminEndpoint() {
    // given — a session holding an authenticated DB user's security context, exactly what a webapp
    // form login leaves behind (the browser then carries the JSESSIONID cookie)
    final SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
    securityContext.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            TestUserDetailsService.DEMO_USERNAME, null, List.of()));
    final MockHttpSession session = new MockHttpSession();
    session.setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

    // when — that session cookie is presented to the cluster-admin API with no Basic credentials
    final MvcTestResult result =
        mockMvcTester
            .get()
            .session(session)
            .uri("https://localhost" + TestApiController.DUMMY_CLUSTER_ADMIN_ENDPOINT)
            .exchange();

    // then — the chain binds a session-free context repository, so the session must not
    // authenticate
    assertThat(result)
        .as("a webapp session cookie must not authenticate the cluster-admin API")
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  public void shouldAllowClusterStatusWithCredentialsUnknownToTheClusterAdminStore() {
    // given / when — a DB-backed user's credentials, which the isolated cluster-admin store does
    // not know. Clients migrating here from /v2/status send whatever they are configured with, so
    // the endpoint must ignore the Authorization header rather than reject it.
    final MvcTestResult result =
        mockMvcTester
            .get()
            .headers(
                basicAuth(
                    TestUserDetailsService.DEMO_USERNAME, TestUserDetailsService.DEMO_USERNAME))
            .uri("https://localhost" + TestApiController.DUMMY_CLUSTER_ADMIN_STATUS_ENDPOINT)
            .exchange();

    // then — no authentication filter runs on this chain, so the header is never inspected
    assertThat(result)
        .as("a foreign credential must not turn the public cluster status into a 401")
        .hasStatus(HttpStatus.OK);
  }

  @Test
  public void shouldRejectUnauthenticatedRequestToClusterExportingStatus() {
    // when — no Authorization header at all, against the real cluster-wide exporting status path
    final MvcTestResult result =
        mockMvcTester
            .get()
            .uri("https://localhost" + TestApiController.DUMMY_CLUSTER_EXPORTING_STATUS_ENDPOINT)
            .exchange();

    // then
    assertThat(result)
        .as("the cluster-wide exporting status must require cluster-admin credentials")
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  public void shouldRejectUnauthenticatedRequestToClusterExportingPause() {
    // when — no Authorization header at all, against the real cluster-wide exporting pause path
    final MvcTestResult result =
        mockMvcTester
            .post()
            .uri("https://localhost" + TestApiController.DUMMY_CLUSTER_EXPORTING_PAUSE_ENDPOINT)
            .exchange();

    // then
    assertThat(result)
        .as("cluster-wide exporting pause must require cluster-admin credentials")
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  public void shouldRejectUnauthenticatedRequestToClusterExportingResume() {
    // when — no Authorization header at all, against the real cluster-wide exporting resume path
    final MvcTestResult result =
        mockMvcTester
            .post()
            .uri("https://localhost" + TestApiController.DUMMY_CLUSTER_EXPORTING_RESUME_ENDPOINT)
            .exchange();

    // then
    assertThat(result)
        .as("cluster-wide exporting resume must require cluster-admin credentials")
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  public void shouldAllowClusterExportingStatusWithClusterAdminCredentials() {
    // when
    final MvcTestResult result =
        mockMvcTester
            .get()
            .headers(basicAuth(CLUSTER_ADMIN_USER, CLUSTER_ADMIN_PASSWORD))
            .uri("https://localhost" + TestApiController.DUMMY_CLUSTER_EXPORTING_STATUS_ENDPOINT)
            .exchange();

    // then
    assertThat(result).hasStatus(HttpStatus.OK);
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
