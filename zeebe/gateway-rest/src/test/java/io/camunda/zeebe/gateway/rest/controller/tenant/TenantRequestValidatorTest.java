/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.tenant;

import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_ILLEGAL_CHARACTER;
import static io.camunda.security.validation.IdentifierValidator.TENANT_ID_MASK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.gateway.mapping.http.validator.TenantRequestValidator;
import io.camunda.gateway.protocol.model.TenantCreateRequest;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class TenantRequestValidatorTest {

  private static final Pattern ID_PATTERN = Pattern.compile(SecurityConfiguration.DEFAULT_ID_REGEX);

  private static final TenantRequestValidator VALIDATOR =
      new TenantRequestValidator(
          new io.camunda.security.validation.TenantValidator(
              new io.camunda.security.validation.IdentifierValidator(ID_PATTERN, ID_PATTERN)));

  @ParameterizedTest
  @MethodSource("validTenantIdsForCreateRequest")
  void shouldPassTenantIdForCreateRequest(final String tenantId) {
    // given
    final var request =
        new TenantCreateRequest()
            .tenantId(tenantId)
            .name("New tenant")
            .description("A new tenant for testing");
    // when
    final var validationResult = VALIDATOR.validateCreateRequest(request);

    // then
    assertThat(validationResult).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("validTenantIds")
  void shouldPassTenantIdForMemberRequest(final String tenantId) {
    // given
    final String memberId = "member_123";

    // when
    final var validationResult =
        VALIDATOR.validateMemberRequest(tenantId, memberId, EntityType.USER);

    // then
    assertThat(validationResult).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("invalidTenantIdsForCreateRequest")
  void shouldFailTenantIdForCreateRequest(final String tenantId, final String expectedMessage) {
    // The strict contract compact constructor rejects invalid tenantIds at construction time,
    // which means they are caught during Jackson deserialization before the validator runs.
    assertThatThrownBy(
            () ->
                new TenantCreateRequest()
                    .tenantId(tenantId)
                    .name("New tenant")
                    .description("A new tenant for testing"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(expectedMessage);
  }

  @ParameterizedTest
  @MethodSource("invalidTenantIds")
  void shouldFailTenantIdForMemberRequest(final String tenantId, final String errorMessage) {
    // given
    final String memberId = "member_123";

    // when
    final var validationResult =
        VALIDATOR.validateMemberRequest(tenantId, memberId, EntityType.USER);

    // then
    assertThat(validationResult)
        .hasValueSatisfying(
            problemDetail -> assertThat(problemDetail.getDetail()).isEqualTo(errorMessage));
  }

  private static Stream<Arguments> validTenantIdsForCreateRequest() {
    // <default> is allowed by the domain validator (special case) but rejected by the
    // compact constructor's OpenAPI pattern, so it cannot be tested through object creation.
    return Stream.of(Arguments.of("custom_1.2-3"));
  }

  private static Stream<Arguments> validTenantIds() {
    return Stream.of(Arguments.of("<default>"), Arguments.of("custom_1.2-3"));
  }

  private static Stream<Arguments> invalidTenantIdsForCreateRequest() {
    return Stream.of(
        Arguments.of(
            "<custom>",
            "The provided tenantId contains illegal characters."
                + " It must match the pattern '^[A-Za-z0-9_@.+-]+$'."),
        Arguments.of("   ", "tenantId must not be blank"),
        Arguments.of("", "tenantId must not be blank"),
        Arguments.of(
            "not blank",
            "The provided tenantId contains illegal characters."
                + " It must match the pattern '^[A-Za-z0-9_@.+-]+$'."));
  }

  private static Stream<Arguments> invalidTenantIds() {
    return Stream.of(
        Arguments.of(
            "<custom>",
            ERROR_MESSAGE_ILLEGAL_CHARACTER.formatted("tenantId", TENANT_ID_MASK) + "."),
        Arguments.of("   ", ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("tenantId") + "."),
        Arguments.of("", ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("tenantId") + "."),
        Arguments.of(
            "not blank",
            ERROR_MESSAGE_ILLEGAL_CHARACTER.formatted("tenantId", TENANT_ID_MASK) + "."));
  }
}
