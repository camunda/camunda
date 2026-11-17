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
    properties = "camunda.webapps.tasklist.ui-enabled=false")
@AutoConfigureTestRestTemplate
@ActiveProfiles("tasklist")
class WebappUiDisabledIT {

  @Autowired private TestRestTemplate restTemplate;

  static Stream<String> unifiedWebappPaths() {
    return Stream.of("/tasklist/", "/tasklist/processes", "/assets/test-asset.js", "/custom.css");
  }

  @ParameterizedTest
  @MethodSource("unifiedWebappPaths")
  void shouldNotExposeUnifiedWebappWhenTasklistUiIsDisabled(final String path) {
    // when
    final var response = restTemplate.getForEntity(path, String.class);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).doesNotContain("unified-webapp-test-shell");
  }
}
