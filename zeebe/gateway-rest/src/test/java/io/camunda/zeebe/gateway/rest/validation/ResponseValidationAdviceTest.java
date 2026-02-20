/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.gateway.protocol.model.LicenseResponse;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.net.URI;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

class ResponseValidationAdviceTest {

  private static ValidatorFactory validatorFactory;
  private static Validator validator;
  private ResponseValidationAdvice advice;
  private ServerHttpRequest mockRequest;
  private ServerHttpResponse mockResponse;

  @BeforeAll
  static void setUpValidator() {
    validatorFactory = Validation.buildDefaultValidatorFactory();
    validator = validatorFactory.getValidator();
  }

  @AfterAll
  static void tearDownValidator() {
    if (validatorFactory != null) {
      validatorFactory.close();
    }
  }

  @BeforeEach
  void setUp() {
    advice = new ResponseValidationAdvice(validator);
    mockRequest = mock(ServerHttpRequest.class);
    mockResponse = mock(ServerHttpResponse.class);
    when(mockRequest.getMethod()).thenReturn(HttpMethod.GET);
    when(mockRequest.getURI()).thenReturn(URI.create("/v2/test"));
  }

  @Test
  void supportsAllReturnTypes() {
    assertThat(advice.supports(null, null)).isTrue();
  }

  /**
   * Helper to invoke beforeBodyWrite with mock request/response objects. The MethodParameter,
   * MediaType, and converter type are not used by the advice logic.
   */
  private Object callBeforeBodyWrite(final Object body) {
    return advice.beforeBodyWrite(
        body,
        null, // MethodParameter — not used in our implementation
        MediaType.APPLICATION_JSON,
        null, // converter type — not used
        mockRequest,
        mockResponse);
  }

  @Nested
  class PassThrough {

    @Test
    void shouldPassThroughNullBody() {
      // when
      final Object result = callBeforeBodyWrite(null);

      // then
      assertThat(result).isNull();
    }

    @Test
    void shouldPassThroughProblemDetailBody() {
      // given
      final ProblemDetail problemDetail =
          ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "some error");

      // when
      final Object result = callBeforeBodyWrite(problemDetail);

      // then — no validation exception, ProblemDetail returned as-is
      assertThat(result).isSameAs(problemDetail);
    }

    @Test
    void shouldPassThroughValidDto() {
      // given — all required fields set
      final LicenseResponse validResponse =
          new LicenseResponse()
              .validLicense(true)
              .licenseType("saas")
              .isCommercial(true)
              .expiresAt("2025-12-31T23:59:59Z");

      // when
      final Object result = callBeforeBodyWrite(validResponse);

      // then
      assertThat(result).isSameAs(validResponse);
    }

    @Test
    void shouldPassThroughPlainStringBody() {
      // given — strings have no bean validation constraints
      final String plainString = "hello";

      // when
      final Object result = callBeforeBodyWrite(plainString);

      // then
      assertThat(result).isSameAs(plainString);
    }
  }

  @Nested
  class ValidationFailures {

    @Test
    void shouldThrowWhenRequiredFieldIsNull() {
      // given — validLicense is null (violates @NotNull)
      final LicenseResponse invalidResponse =
          new LicenseResponse()
              .licenseType("saas")
              .isCommercial(true)
              .expiresAt("2025-12-31T23:59:59Z");

      // when / then
      assertThatThrownBy(() -> callBeforeBodyWrite(invalidResponse))
          .isInstanceOf(ResponseValidationException.class)
          .hasMessageContaining("validLicense");
    }

    @Test
    void shouldThrowWhenMultipleRequiredFieldsAreNull() {
      // given — all required fields are null (empty DTO)
      final LicenseResponse invalidResponse = new LicenseResponse();

      // when / then
      assertThatThrownBy(() -> callBeforeBodyWrite(invalidResponse))
          .isInstanceOf(ResponseValidationException.class)
          .satisfies(
              ex -> {
                final ResponseValidationException rve = (ResponseValidationException) ex;
                assertThat(rve.getViolations()).hasSizeGreaterThanOrEqualTo(3);
                assertThat(rve.getMessage())
                    .contains("validLicense", "licenseType", "isCommercial");
              });
    }

    @Test
    void shouldIncludePropertyPathInViolationMessage() {
      // given
      final LicenseResponse invalidResponse =
          new LicenseResponse()
              .validLicense(true)
              .isCommercial(true)
              .expiresAt("2025-12-31T23:59:59Z");
      // licenseType is null

      // when / then
      assertThatThrownBy(() -> callBeforeBodyWrite(invalidResponse))
          .isInstanceOf(ResponseValidationException.class)
          .hasMessageContaining("licenseType")
          .hasMessageContaining("must not be null");
    }
  }

  @Nested
  class ExceptionDetails {

    @Test
    void shouldExposeViolationsInException() {
      // given — only licenseType is null
      final LicenseResponse invalidResponse =
          new LicenseResponse()
              .validLicense(true)
              .isCommercial(true)
              .expiresAt("2025-12-31T23:59:59Z");

      // when / then
      assertThatThrownBy(() -> callBeforeBodyWrite(invalidResponse))
          .isInstanceOf(ResponseValidationException.class)
          .satisfies(
              ex -> {
                final ResponseValidationException rve = (ResponseValidationException) ex;
                assertThat(rve.getViolations()).hasSize(1);
                assertThat(rve.getViolations().iterator().next().getPropertyPath().toString())
                    .isEqualTo("licenseType");
              });
    }
  }
}
