/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapp;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.webapptest.TestWebappApplication;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    classes = TestWebappApplication.class,
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = "spring.thymeleaf.prefix=classpath:/META-INF/resources/")
@AutoConfigureTestRestTemplate
@ActiveProfiles("tasklist")
class WebappRoutingIT {

  @Autowired private TestRestTemplate restTemplate;

  static Stream<String> tasklistRoutes() {
    return Stream.of(
        "/tasklist",
        "/tasklist/",
        "/tasklist/index.html",
        "/tasklist/processes",
        "/tasklist/123/history/456",
        "/tasklist/assets-overview");
  }

  @ParameterizedTest
  @MethodSource("tasklistRoutes")
  void shouldServeUnifiedShellForTasklistRoutes(final String route) {
    // when
    final var response = restTemplate.getForEntity(route, String.class);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("unified-webapp-test-shell")
        .contains("data-base-name=\"/\"")
        .contains("<base href=\"/\"")
        .doesNotContain("/tasklist/tasklist");
  }

  static Stream<String> removedTasklistResources() {
    return Stream.of(
        "/tasklist/assets/missing.js",
        "/tasklist/client-config.js",
        "/tasklist/custom.css",
        "/tasklist/favicon.ico");
  }

  @ParameterizedTest
  @MethodSource("removedTasklistResources")
  void shouldNotServeUnifiedShellForRemovedTasklistResources(final String route) {
    // when
    final var response = restTemplate.getForEntity(route, String.class);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).doesNotContain("unified-webapp-test-shell");
  }

  @Test
  void shouldNotServeUnifiedShellForNonGetRequests() {
    // when
    final var response =
        restTemplate.exchange("/tasklist/processes", HttpMethod.POST, null, String.class);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(response.getBody()).doesNotContain("unified-webapp-test-shell");
  }

  @Test
  void shouldServePhysicalTenantTasklistAtTenantRootBaseName() {
    // when
    final var response =
        restTemplate.getForEntity("/physical-tenants/tenant-a/tasklist/processes", String.class);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("unified-webapp-test-shell")
        .contains("data-context-path=\"/physical-tenants/tenant-a\"")
        .contains("data-base-name=\"/physical-tenants/tenant-a/\"")
        .contains("<base href=\"/physical-tenants/tenant-a/\"")
        .doesNotContain("/tasklist/tasklist");
  }

  @Test
  void shouldNotExposeWebappControllerNamespace() {
    // when
    final var response = restTemplate.getForEntity("/webapp", String.class);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
