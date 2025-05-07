/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static io.camunda.authentication.config.OrganizationValidator.ORGANIZATION_CLAIM_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.Jwt;

final class OrganizationValidatorTest {

  @Test
  void shouldThrowExceptionWhenOrganizationIdIsNull() {
    // when/then
    assertThatThrownBy(() -> new OrganizationValidator(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("organizationId must not be null");
  }

  @Test
  void shouldValidateTokenWithMatchingOrganizationId() {
    // given
    final var organizationId = "valid-org-id";
    final var validator = new OrganizationValidator(organizationId);
    final var token = createJwtWithOrganization(organizationId);

    // when
    final var result = validator.validate(token);

    // then
    assertThat(result.hasErrors()).isFalse();
  }

  @Test
  void shouldValidateTokenWithMatchingOrganizationIdAmongMany() {
    // given
    final var organizationId = "valid-org-id";
    final var validator = new OrganizationValidator(organizationId);
    final var token =
        createJwtWithMultipleOrganizations(List.of("other-org-id", organizationId, "third-org-id"));

    // when
    final var result = validator.validate(token);

    // then
    assertThat(result.hasErrors()).isFalse();
  }

  @Test
  void shouldRejectTokenWithNoMatchingOrganizationId() {
    // given
    final var organizationId = "valid-org-id";
    final var validator = new OrganizationValidator(organizationId);
    final var token = createJwtWithOrganization("invalid-org-id");

    // when
    final var result = validator.validate(token);

    // then
    assertThat(result.hasErrors()).isTrue();

    final var errors = result.getErrors();
    assertThat(errors).hasSize(1);

    final var error = errors.iterator().next();
    assertThat(error.getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_TOKEN);
    assertThat(error.getDescription())
        .contains("Token claims organizations")
        .contains("expected " + organizationId);
  }

  @Test
  void shouldRejectTokenWithNoOrganizationClaim() {
    // given
    final var organizationId = "valid-org-id";
    final var validator = new OrganizationValidator(organizationId);
    final var token = createJwtWithoutOrganizationClaim();

    // when
    final var result = validator.validate(token);

    // then
    assertThat(result.hasErrors()).isTrue();

    final var errors = result.getErrors();
    assertThat(errors).hasSize(1);

    final var error = errors.iterator().next();
    assertThat(error.getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_TOKEN);
    assertThat(error.getDescription())
        .contains("Token claims organizations")
        .contains("expected " + organizationId);
  }

  @Test
  void shouldRejectTokenWithInvalidOrganizationClaimFormat() {
    // given
    final var organizationId = "valid-org-id";
    final var validator = new OrganizationValidator(organizationId);
    final var token = createJwtWithInvalidOrganizationFormat();

    // when
    final var result = validator.validate(token);

    // then
    assertThat(result.hasErrors()).isTrue();

    final var errors = result.getErrors();
    assertThat(errors).hasSize(1);

    final var error = errors.iterator().next();
    assertThat(error.getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_TOKEN);
    assertThat(error.getDescription())
        .contains("Token claims organizations")
        .contains("expected " + organizationId);
  }

  private static Jwt createJwtWithOrganization(final String organizationId) {
    return new Jwt(
        "token-value",
        Instant.now(),
        Instant.now().plusSeconds(60),
        Map.of("alg", "RS256"),
        Map.of(ORGANIZATION_CLAIM_KEY, List.of(Map.of("id", organizationId))));
  }

  private static Jwt createJwtWithMultipleOrganizations(final List<String> organizationIds) {
    return new Jwt(
        "token-value",
        Instant.now(),
        Instant.now().plusSeconds(60),
        Map.of("alg", "RS256"),
        Map.of(
            ORGANIZATION_CLAIM_KEY, organizationIds.stream().map(id -> Map.of("id", id)).toList()));
  }

  private static Jwt createJwtWithoutOrganizationClaim() {
    return new Jwt(
        "token-value",
        Instant.now(),
        Instant.now().plusSeconds(60),
        Map.of("alg", "RS256"),
        Map.of("sub", "user"));
  }

  private static Jwt createJwtWithInvalidOrganizationFormat() {
    return new Jwt(
        "token-value",
        Instant.now(),
        Instant.now().plusSeconds(60),
        Map.of("alg", "RS256"),
        Map.of(ORGANIZATION_CLAIM_KEY, "not-a-collection"));
  }
}
