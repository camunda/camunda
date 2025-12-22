/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.controllers;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.tasklist.util.TasklistIntegrationTest;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings.Redirects;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class OldRoutesRedirectionControllerIT extends TasklistIntegrationTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  private String baseUrl() {
    return "http://localhost:" + port;
  }

  static Stream<String> redirectionTestDataProvider() {
    return Stream.of(
        "", "/processes", "/login", "/123456", "/processes/order/start", "/new/process_id");
  }

  @ParameterizedTest
  @MethodSource("redirectionTestDataProvider")
  public void testRedirections(final String path) {
    final ResponseEntity<String> response =
        restTemplate
            .withRedirects(Redirects.DONT_FOLLOW)
            .getForEntity(baseUrl() + path, String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(response.getHeaders().getLocation().toString())
        .isEqualTo(baseUrl() + "/tasklist" + path);
  }

  static Stream<String> notFoundTestDataProvider() {
    return Stream.of("/v1/user-tasks", "/decisions", "/a12345", "/new");
  }

  @ParameterizedTest
  @MethodSource("notFoundTestDataProvider")
  public void testNotFound(final String path) {
    final ResponseEntity<String> response =
        restTemplate.getForEntity(baseUrl() + path, String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
