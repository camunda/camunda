/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.controllers;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.webapps.WebappsModuleConfiguration;
import io.camunda.webapps.controllers.BackupController;
import io.camunda.webapps.controllers.IndexController;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@AutoConfigureTestRestTemplate
@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      OperateIndexController.class,
      IndexController.class,
      WebappsModuleConfiguration.class,
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class OldRoutesRedirectionControllerIT {

  @MockitoBean private BackupController backupController;

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  private String baseUrl() {
    return "http://localhost:" + port;
  }

  static Stream<String> redirectionTestDataProvider() {
    return Stream.of(
        "",
        "/processes",
        "/processes/order",
        "/login",
        "/decisions",
        "/decisions/order",
        "/instances",
        "/instances/12345");
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
        .isEqualTo(baseUrl() + "/operate" + path);
  }

  static Stream<String> notFoundTestDataProvider() {
    return Stream.of("/v1/user-tasks", "/process/order/start", "/12345");
  }

  @ParameterizedTest
  @MethodSource("notFoundTestDataProvider")
  public void testNotFound(final String path) {
    final ResponseEntity<String> response =
        restTemplate.getForEntity(baseUrl() + path, String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
