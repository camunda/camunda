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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies that static assets under {@code /webapp/assets/**} are served with forever-caching
 * headers and that requests for non-existent assets return 404 rather than being silently resolved
 * by the resource handler.
 */
@SpringBootTest(classes = TestWebappApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("tmp-webapp")
class WebappCacheHeadersIT {

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void shouldServeWebappAssetsWithImmutableForeverCacheHeader() {
    // when
    final ResponseEntity<String> response =
        restTemplate.getForEntity("/webapp/assets/test-asset.js", String.class);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getCacheControl())
        .as("forever-cache header on /webapp/assets/**")
        .isEqualTo("max-age=31536000, public, immutable");
  }

  @Test
  void shouldNotServeUnknownAssetsAsStaticResources() {
    // when — ensures the resource handler does not silently 200 on missing files
    final ResponseEntity<String> response =
        restTemplate.getForEntity("/webapp/assets/does-not-exist.js", String.class);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
