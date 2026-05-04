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
   * Helper to create a {@link LicenseResponse} with arbitrary field values via reflection,
   * bypassing the protected constructor and staged builder. This lets tests intentionally create
   * invalid/incomplete responses to verify validation logic.
   *
   * @param validLicense value for the {@code validLicense} field (may be {@code null})
   * @param licenseType value for the {@code licenseType} field (may be {@code null})
   * @param isCommercial value for the {@code isCommercial} field (may be {@code null})
   * @param expiresAt value for the {@code expiresAt} field (may be {@code null})
   */
  @SuppressWarnings("NullAway")
  private static LicenseResponse licenseResponseViaReflection(
      final Boolean validLicense,
      final String licenseType,
      final Boolean isCommercial,
      final String expiresAt) {
    try {
      final var ctor =
          LicenseResponse.class.getDeclaredConstructor(
              Boolean.class, String.class, Boolean.class, String.class);
      ctor.setAccessible(true);
      return ctor.newInstance(validLicense, licenseType, isCommercial, expiresAt);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
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
          LicenseResponse.Builder.builder()
              .validLicense(true)
              .licenseType("saas")
              .isCommercial(true)
              .expiresAt("2025-12-31T23:59:59Z")
              .build();

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
          licenseResponseViaReflection(null, "saas", true, "2025-12-31T23:59:59Z");

      // when / then
      assertThatThrownBy(() -> callBeforeBodyWrite(invalidResponse))
          .isInstanceOf(ResponseValidationException.class)
          .hasMessageContaining("validLicense");
    }

    @Test
    void shouldThrowWhenMultipleRequiredFieldsAreNull() {
      // given — all required fields are null
      final LicenseResponse invalidResponse = licenseResponseViaReflection(null, null, null, null);

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
      // given — licenseType is null
      final LicenseResponse invalidResponse =
          licenseResponseViaReflection(true, null, true, "2025-12-31T23:59:59Z");

      // when / then
      assertThatThrownBy(() -> callBeforeBodyWrite(invalidResponse))
          .isInstanceOf(ResponseValidationException.class)
          .hasMessageContaining("licenseType")
          .hasMessageContaining("must not be null");
    }

    @Test
    void shouldIncludeInvalidValueInViolationMessage() {
      // given — licenseType is null
      final LicenseResponse invalidResponse =
          licenseResponseViaReflection(true, null, true, "2025-12-31T23:59:59Z");

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
      final LicenseResponse invalidResponse =
          licenseResponseViaReflection(true, null, true, "2025-12-31T23:59:59Z");

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
