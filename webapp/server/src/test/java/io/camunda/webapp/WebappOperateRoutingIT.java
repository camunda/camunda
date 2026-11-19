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
      "spring.thymeleaf.prefix=classpath:/META-INF/resources/",
      "camunda.webapps.operate.unified-ui=true"
    })
@AutoConfigureTestRestTemplate
@ActiveProfiles("tasklist")
class WebappOperateRoutingIT {

  @Autowired private TestRestTemplate restTemplate;

  static Stream<String> operateRoutes() {
    return Stream.of(
        "/operate",
        "/operate/",
        "/operate/index.html",
        "/operate/processes",
        "/operate/processes/2251799813685249");
  }

  @ParameterizedTest
  @MethodSource("operateRoutes")
  void shouldServeUnifiedShellForOperateRoutes(final String route) {
    // when
    final var response = restTemplate.getForEntity(route, String.class);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("unified-webapp-test-shell")
        .contains("data-base-name=\"/\"")
        .doesNotContain("/operate/operate");
  }

  @Test
  void shouldNotServeUnifiedShellForOperateAssets() {
    // when
    final var response = restTemplate.getForEntity("/operate/assets/missing.js", String.class);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).doesNotContain("unified-webapp-test-shell");
  }
}
