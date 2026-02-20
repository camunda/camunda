/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validation;

import io.camunda.gateway.protocol.model.LicenseResponse;
import io.camunda.zeebe.gateway.rest.RestTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Integration test for the Gateway response validation. Tests the full request → controller →
 * response validation → error handling flow using a dedicated test controller.
 */
class ResponseValidationSpringMvcIntegrationTest {

  /**
   * Test controller that returns DTOs with known validation states. This avoids coupling to real
   * controller logic and lets us control exactly which fields are null.
   */
  @RestController
  static class TestResponseValidationController {

    @GetMapping("/v2/test/response-validation/valid")
    public LicenseResponse validResponse() {
      return new LicenseResponse()
          .validLicense(true)
          .licenseType("saas")
          .isCommercial(true)
          .expiresAt("2025-12-31T23:59:59Z");
    }

    @GetMapping("/v2/test/response-validation/invalid")
    public LicenseResponse invalidResponse() {
      // Missing licenseType (violates @NotNull)
      return new LicenseResponse()
          .validLicense(true)
          .isCommercial(true)
          .expiresAt("2025-12-31T23:59:59Z");
    }

    @GetMapping("/v2/test/response-validation/all-null")
    public LicenseResponse allNullResponse() {
      // All required fields null
      return new LicenseResponse();
    }

    @GetMapping("/v2/test/response-validation/null-body")
    public ResponseEntity<Object> nullBody() {
      return ResponseEntity.noContent().build();
    }
  }

  /**
   * Tests that response validation is active and catches invalid responses when {@code
   * camunda.rest.response-validation.enabled=true}.
   */
  @Nested
  @WebMvcTest(value = TestResponseValidationController.class)
  @Import(ResponseValidationAdvice.class)
  @TestPropertySource(properties = "camunda.rest.response-validation.enabled=true")
  class ResponseValidationEnabled extends RestTest {

    @Test
    void shouldReturn500WhenResponseViolatesContract() {
      webClient
          .get()
          .uri("/v2/test/response-validation/invalid")
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .is5xxServerError()
          .expectBody()
          .jsonPath("$.title")
          .isEqualTo("Response Validation Failed")
          .jsonPath("$.detail")
          .value(
              detail -> {
                final String detailStr = (String) detail;
                org.assertj.core.api.Assertions.assertThat(detailStr)
                    .contains("licenseType")
                    .contains("must not be null");
              });
    }

    @Test
    void shouldReturn500WhenAllRequiredFieldsAreNull() {
      webClient
          .get()
          .uri("/v2/test/response-validation/all-null")
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .is5xxServerError()
          .expectBody()
          .jsonPath("$.title")
          .isEqualTo("Response Validation Failed")
          .jsonPath("$.detail")
          .value(
              detail -> {
                final String detailStr = (String) detail;
                org.assertj.core.api.Assertions.assertThat(detailStr)
                    .contains("validLicense")
                    .contains("licenseType")
                    .contains("isCommercial");
              });
    }

    @Test
    void shouldReturn200WhenResponseIsValid() {
      webClient
          .get()
          .uri("/v2/test/response-validation/valid")
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$.validLicense")
          .isEqualTo(true)
          .jsonPath("$.licenseType")
          .isEqualTo("saas")
          .jsonPath("$.isCommercial")
          .isEqualTo(true);
    }

    @Test
    void shouldHandle204NoContentGracefully() {
      webClient
          .get()
          .uri("/v2/test/response-validation/null-body")
          .exchange()
          .expectStatus()
          .isNoContent();
    }
  }

  /**
   * Tests that when response validation is disabled (default), invalid responses pass through
   * without validation errors.
   */
  @Nested
  @WebMvcTest(value = TestResponseValidationController.class)
  class ResponseValidationDisabled extends RestTest {

    @Test
    void shouldReturn200EvenWhenResponseViolatesContract() {
      // without response validation, the invalid response is returned as-is
      webClient
          .get()
          .uri("/v2/test/response-validation/invalid")
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$.validLicense")
          .isEqualTo(true)
          .jsonPath("$.licenseType")
          .doesNotExist();
    }
  }
}
