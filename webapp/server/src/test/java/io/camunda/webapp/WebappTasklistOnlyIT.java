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
      "camunda.webapps.operate.ui-enabled=false",
      "spring.thymeleaf.prefix=classpath:/META-INF/resources/"
    })
@AutoConfigureTestRestTemplate
@ActiveProfiles({"operate", "tasklist"})
class WebappTasklistOnlyIT {

  @Autowired private TestRestTemplate restTemplate;

  static Stream<String> operateRoutes() {
    return Stream.of("/operate", "/operate/", "/operate/processes");
  }

  @ParameterizedTest
  @MethodSource("operateRoutes")
  void shouldNotServeOperateWhenOperateUiIsDisabled(final String route) {
    final var response = restTemplate.getForEntity(route, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).doesNotContain("unified-webapp-test-shell");
  }

  static Stream<String> tasklistRoutes() {
    return Stream.of("/tasklist", "/tasklist/", "/tasklist/processes");
  }

  @ParameterizedTest
  @MethodSource("tasklistRoutes")
  void shouldServeTasklistWhenOperateUiIsDisabled(final String route) {
    final var response = restTemplate.getForEntity(route, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("unified-webapp-test-shell");
  }

  @Test
  void shouldServeAssetsWhenOperateUiIsDisabled() {
    final var response = restTemplate.getForEntity("/assets/test-asset.js", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
