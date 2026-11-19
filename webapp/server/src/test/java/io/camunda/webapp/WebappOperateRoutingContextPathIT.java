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
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    classes = TestWebappApplication.class,
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      "server.servlet.context-path=/camunda",
      "spring.thymeleaf.prefix=classpath:/META-INF/resources/"
    })
@AutoConfigureTestRestTemplate
@ActiveProfiles("operate")
class WebappOperateRoutingContextPathIT {

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void shouldServeOperateUnderServletContextPath() {
    // when
    final var response =
        restTemplate.getForEntity(
            "/operate/processes/2251799813685249?tab=incidents", String.class);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("unified-webapp-test-shell")
        .contains("data-context-path=\"/camunda\"")
        .contains("data-base-name=\"/camunda/\"")
        .contains("<base href=\"/camunda/\"")
        .doesNotContain("/operate/operate");
  }

  @Test
  void shouldServePhysicalTenantOperateUnderServletContextPath() {
    // when
    final var response =
        restTemplate.getForEntity("/physical-tenants/tenant-a/operate/processes", String.class);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("data-context-path=\"/camunda/physical-tenants/tenant-a\"")
        .contains("data-base-name=\"/camunda/physical-tenants/tenant-a/\"")
        .contains("<base href=\"/camunda/physical-tenants/tenant-a/\"")
        .doesNotContain("/operate/operate");
  }

  static Stream<String> assets() {
    return Stream.of("/assets/test-asset.js", "/physical-tenants/tenant-a/assets/test-asset.js");
  }

  @ParameterizedTest
  @MethodSource("assets")
  void shouldServeAssetsUnderServletContextPath(final String path) {
    // when
    final var response = restTemplate.getForEntity(path, String.class);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getCacheControl())
        .isEqualTo("max-age=31536000, public, immutable");
  }

  static Stream<String> clientConfigPaths() {
    return Stream.of(
        "/operate/client-config.js", "/physical-tenants/tenant-a/operate/client-config.js");
  }

  @ParameterizedTest
  @MethodSource("clientConfigPaths")
  void shouldNotServeUnifiedShellForClientConfigUnderServletContextPath(final String path) {
    // when
    final var response = restTemplate.getForEntity(path, String.class);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).doesNotContain("unified-webapp-test-shell");
  }
}
