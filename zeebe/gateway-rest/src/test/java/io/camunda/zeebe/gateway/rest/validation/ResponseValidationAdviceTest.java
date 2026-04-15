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

import io.camunda.gateway.mapping.http.search.contract.generated.LicenseResponseContract;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
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

  /**
   * Test-only DTO that allows null construction for Bean Validation testing. The generated strict
   * contract records use compact constructors that reject nulls at construction time, preventing
   * their use in tests that need to verify Bean Validation catches null required fields.
   */
  record TestResponseDto(
      @NotNull Boolean validLicense,
      @NotNull String licenseType,
      @NotNull Boolean isCommercial,
      String expiresAt) {}

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
      final var validResponse =
          new LicenseResponseContract(true, "saas", true, "2025-12-31T23:59:59Z");

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
      final var invalidResponse = new TestResponseDto(null, "saas", true, "2025-12-31T23:59:59Z");

      // when / then
      assertThatThrownBy(() -> callBeforeBodyWrite(invalidResponse))
          .isInstanceOf(ResponseValidationException.class)
          .hasMessageContaining("validLicense");
    }

    @Test
    void shouldThrowWhenMultipleRequiredFieldsAreNull() {
      // given — all required fields are null (empty DTO)
      final var invalidResponse = new TestResponseDto(null, null, null, null);

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
      // given — only licenseType is null
      final var invalidResponse = new TestResponseDto(true, null, true, "2025-12-31T23:59:59Z");

      // when / then
      assertThatThrownBy(() -> callBeforeBodyWrite(invalidResponse))
          .isInstanceOf(ResponseValidationException.class)
          .hasMessageContaining("licenseType")
          .hasMessageContaining("must not be null");
    }

    @Test
    void shouldIncludeInvalidValueInViolationMessage() {
      // given — licenseType is null
      final var invalidResponse = new TestResponseDto(true, null, true, "2025-12-31T23:59:59Z");

      // when / then
      assertThatThrownBy(() -> callBeforeBodyWrite(invalidResponse))
          .isInstanceOf(ResponseValidationException.class)
          .hasMessageContaining("licenseType: must not be null (was: null)");
    }
  }

  @Nested
  class ExceptionDetails {

    @Test
    void shouldExposeViolationsInException() {
      // given — only licenseType is null
      final var invalidResponse = new TestResponseDto(true, null, true, "2025-12-31T23:59:59Z");

      // when / then
      assertThatThrownBy(() -> callBeforeBodyWrite(invalidResponse))
          .isInstanceOf(ResponseValidationException.class)
          .satisfies(
              ex -> {
                final ResponseValidationException rve = (ResponseValidationException) ex;
                assertThat(rve.getViolations()).hasSize(1);
                final var violation = rve.getViolations().iterator().next();
                assertThat(violation.getPropertyPath().toString()).isEqualTo("licenseType");
                assertThat(violation.getInvalidValue()).isNull();
              });
    }
  }
}
