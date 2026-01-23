/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.controllers;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.application.commons.security.CamundaSecurityConfiguration.CamundaSecurityProperties;
import io.camunda.security.configuration.InitializationConfiguration;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;

@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class OldRoutesRedirectionControllerIT extends TasklistIntegrationTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private CamundaSecurityProperties securityConfig;

  private String baseUrl() {
    return "http://localhost:" + port;
  }

  static Stream<String> redirectionTestDataProvider() {
    return Stream.of(
        "", "/processes", "/login", "/123456", "/processes/order/start", "/new/process_id");
  }

  @BeforeEach
  public void setup() {
    // force a default admin role so that AdminUserCheckFilter does not interfere and redirect to
    // /identity/setup as otherwise we try to search in secondary storage for the roles and then
    // things may or may not work depending on what else ran before
    securityConfig
        .getInitialization()
        .getDefaultRoles()
        .put("admin", Map.of("users", List.of(InitializationConfiguration.DEFAULT_USER_USERNAME)));
  }

  @ParameterizedTest
  @MethodSource("redirectionTestDataProvider")
  public void testRedirections(final String path) {
    final ResponseEntity<String> response =
        restTemplate
            .withRedirects(HttpRedirects.DONT_FOLLOW)
            .getForEntity(baseUrl() + path, String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(response.getHeaders().getLocation().toString())
        .isEqualTo(baseUrl() + "/tasklist" + path);
  }

  static Stream<String> notFoundTestDataProvider() {
    return Stream.of("/v1/user-tasks", "/decisions", "/new");
  }

  @ParameterizedTest
  @MethodSource("notFoundTestDataProvider")
  public void testNotFound(final String path) {
    final ResponseEntity<String> response =
        restTemplate.getForEntity(baseUrl() + path, String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
