/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.tenant;

import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_ILLEGAL_CHARACTER;
import static io.camunda.zeebe.gateway.rest.validator.IdentifierValidator.TENANT_ID_MASK;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.protocol.model.TenantCreateRequest;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.gateway.rest.validator.TenantRequestValidator;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class TenantRequestValidatorTest {

  private static final Pattern ID_PATTERN = Pattern.compile(SecurityConfiguration.DEFAULT_ID_REGEX);

  @ParameterizedTest
  @MethodSource("validTenantIds")
  void shouldPassTenantIdForCreateRequest(final String tenantId) {
    // given
    final TenantCreateRequest request =
        new TenantCreateRequest()
            .tenantId(tenantId)
            .name("New tenant")
            .description("A new tenant for testing");

    // when
    final var validationResult = TenantRequestValidator.validateCreateRequest(request);

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
        TenantRequestValidator.validateMemberRequest(
            tenantId, memberId, EntityType.USER, ID_PATTERN);

    // then
    assertThat(validationResult).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("invalidTenantIds")
  void shouldFailTenantIdForCreateRequest(final String tenantId, final String errorMessage) {
    // given
    final TenantCreateRequest request =
        new TenantCreateRequest()
            .tenantId(tenantId)
            .name("New tenant")
            .description("A new tenant for testing");

    // when
    final var validationResult = TenantRequestValidator.validateCreateRequest(request);

    // then
    assertThat(validationResult)
        .hasValueSatisfying(
            problemDetail -> assertThat(problemDetail.getDetail()).isEqualTo(errorMessage));
  }

  @ParameterizedTest
  @MethodSource("invalidTenantIds")
  void shouldFailTenantIdForMemberRequest(final String tenantId, final String errorMessage) {
    // given
    final String memberId = "member_123";

    // when
    final var validationResult =
        TenantRequestValidator.validateMemberRequest(
            tenantId, memberId, EntityType.USER, ID_PATTERN);

    // then
    assertThat(validationResult)
        .hasValueSatisfying(
            problemDetail -> assertThat(problemDetail.getDetail()).isEqualTo(errorMessage));
  }

  private static Stream<Arguments> validTenantIds() {
    return Stream.of(Arguments.of("<default>"), Arguments.of("custom_1.2-3"));
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
