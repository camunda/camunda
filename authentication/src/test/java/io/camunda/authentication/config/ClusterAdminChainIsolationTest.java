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
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
