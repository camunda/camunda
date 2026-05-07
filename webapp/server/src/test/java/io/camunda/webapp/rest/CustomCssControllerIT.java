/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapp.rest;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.webapptest.TestWebappApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/** Integration test for {@link CustomCssController}. */
@SpringBootTest(classes = TestWebappApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("tmp-webapp")
class CustomCssControllerIT {

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void shouldReturn200() {
    // when
    final ResponseEntity<String> response =
        restTemplate.getForEntity(CustomCssController.PATH, String.class);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(200);
  }

  @Test
  void shouldReturnTextCssContentType() {
    // when
    final ResponseEntity<String> response =
        restTemplate.getForEntity(CustomCssController.PATH, String.class);

    // then
    final var contentType = response.getHeaders().getContentType();
    assertThat(contentType).isNotNull();
    assertThat(contentType.getType()).isEqualTo("text");
    assertThat(contentType.getSubtype()).isEqualTo("css");
  }

  @Test
  void shouldReturnNoCacheHeader() {
    // when
    final ResponseEntity<String> response =
        restTemplate.getForEntity(CustomCssController.PATH, String.class);

    // then — operators may swap custom.css between deployments; stale caches must not persist
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-cache");
  }

  @Test
  void shouldReturnEmptyBodyWhenNoFilePresent() {
    // when
    final ResponseEntity<String> response =
        restTemplate.getForEntity(CustomCssController.PATH, String.class);

    // then — no custom.css on test classpath → body is empty/null, never a 404 error payload
    assertThat(response.getBody()).isNullOrEmpty();
  }
}
