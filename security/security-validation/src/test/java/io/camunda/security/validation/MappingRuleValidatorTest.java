/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.validation;

import static io.camunda.security.validation.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.security.validation.ErrorMessages.ERROR_MESSAGE_ILLEGAL_CHARACTER;
import static io.camunda.security.validation.ErrorMessages.ERROR_MESSAGE_TOO_MANY_CHARACTERS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MappingRuleValidatorTest {

  private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_~@.+-]+$");

  private static final MappingRuleValidator VALIDATOR =
      new MappingRuleValidator(new IdentifierValidator(ID_PATTERN, ID_PATTERN));

  // ---- validateCreateRequest ----

  @Test
  void shouldReturnNoViolationsForValidCreateRequest() {
    // when:
    final List<String> violations =
        VALIDATOR.validateCreateRequest("rule-1", "email", "user@example.com", "My Rule");

    // then:
    assertThat(violations).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void shouldRejectMissingMappingRuleIdOnCreate(final String mappingRuleId) {
    // when:
    final List<String> violations =
        VALIDATOR.validateCreateRequest(mappingRuleId, "email", "user@example.com", "My Rule");

    // then:
    assertThat(violations).contains(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("mappingRuleId"));
  }

  @Test
  void shouldRejectMappingRuleIdWithIllegalCharacters() {
    // when:
    final List<String> violations =
        VALIDATOR.validateCreateRequest("invalid id!", "email", "user@example.com", "My Rule");

    // then:
    assertThat(violations)
        .contains(ERROR_MESSAGE_ILLEGAL_CHARACTER.formatted("mappingRuleId", ID_PATTERN));
  }

  @Test
  void shouldRejectMappingRuleIdExceedingMaxLength() {
    // given:
    final String longId = "a".repeat(ValidationConstants.MAX_FIELD_LENGTH + 1);

    // when:
    final List<String> violations =
        VALIDATOR.validateCreateRequest(longId, "email", "user@example.com", "My Rule");

    // then:
    assertThat(violations)
        .contains(
            ERROR_MESSAGE_TOO_MANY_CHARACTERS.formatted(
                "mappingRuleId", ValidationConstants.MAX_FIELD_LENGTH));
  }

  @Test
  void shouldAcceptMappingRuleIdAtMaxLength() {
    // given:
    final String maxId = "a".repeat(ValidationConstants.MAX_FIELD_LENGTH);

    // when:
    final List<String> violations =
        VALIDATOR.validateCreateRequest(maxId, "email", "user@example.com", "My Rule");

    // then:
    assertThat(violations).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void shouldRejectMissingClaimNameOnCreate(final String claimName) {
    // when:
    final List<String> violations =
        VALIDATOR.validateCreateRequest("rule-1", claimName, "user@example.com", "My Rule");

    // then:
    assertThat(violations).contains(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("claimName"));
  }

  @Test
  void shouldRejectClaimNameExceedingMaxLength() {
    // given:
    final String longClaimName = "a".repeat(ValidationConstants.MAX_FIELD_LENGTH + 1);

    // when:
    final List<String> violations =
        VALIDATOR.validateCreateRequest("rule-1", longClaimName, "user@example.com", "My Rule");

    // then:
    assertThat(violations)
        .contains(
            ERROR_MESSAGE_TOO_MANY_CHARACTERS.formatted(
                longClaimName, ValidationConstants.MAX_FIELD_LENGTH));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void shouldRejectMissingClaimValueOnCreate(final String claimValue) {
    // when:
    final List<String> violations =
        VALIDATOR.validateCreateRequest("rule-1", "email", claimValue, "My Rule");

    // then:
    assertThat(violations).contains(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("claimValue"));
  }

  @Test
  void shouldRejectClaimValueExceedingMaxLength() {
    // given:
    final String longClaimValue = "a".repeat(ValidationConstants.MAX_FIELD_LENGTH + 1);

    // when:
    final List<String> violations =
        VALIDATOR.validateCreateRequest("rule-1", "email", longClaimValue, "My Rule");

    // then:
    assertThat(violations)
        .contains(
            ERROR_MESSAGE_TOO_MANY_CHARACTERS.formatted(
                longClaimValue, ValidationConstants.MAX_FIELD_LENGTH));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void shouldRejectMissingNameOnCreate(final String name) {
    // when:
    final List<String> violations =
        VALIDATOR.validateCreateRequest("rule-1", "email", "user@example.com", name);

    // then:
    assertThat(violations).contains(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("name"));
  }

  @Test
  void shouldRejectNameExceedingMaxLengthOnCreate() {
    // given:
    final String longName = "a".repeat(ValidationConstants.MAX_FIELD_LENGTH + 1);

    // when:
    final List<String> violations =
        VALIDATOR.validateCreateRequest("rule-1", "email", "user@example.com", longName);

    // then:
    assertThat(violations)
        .contains(
            ERROR_MESSAGE_TOO_MANY_CHARACTERS.formatted(
                longName, ValidationConstants.MAX_FIELD_LENGTH));
  }

  @Test
  void shouldAcceptNameAtMaxLengthOnCreate() {
    // given:
    final String maxName = "a".repeat(ValidationConstants.MAX_FIELD_LENGTH);

    // when:
    final List<String> violations =
        VALIDATOR.validateCreateRequest("rule-1", "email", "user@example.com", maxName);

    // then:
    assertThat(violations).isEmpty();
  }

  @Test
  void shouldReturnAllViolationsWhenAllFieldsMissingOnCreate() {
    // when:
    final List<String> violations = VALIDATOR.validateCreateRequest(null, null, null, null);

    // then:
    assertThat(violations)
        .containsExactlyInAnyOrder(
            ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("claimName"),
            ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("claimValue"),
            ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("name"),
            ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("mappingRuleId"));
  }

  // ---- validateUpdateRequest ----

  @Test
  void shouldReturnNoViolationsForValidUpdateRequest() {
    // when:
    final List<String> violations =
        VALIDATOR.validateUpdateRequest("email", "user@example.com", "My Rule");

    // then:
    assertThat(violations).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void shouldRejectMissingClaimNameOnUpdate(final String claimName) {
    // when:
    final List<String> violations =
        VALIDATOR.validateUpdateRequest(claimName, "user@example.com", "My Rule");

    // then:
    assertThat(violations).contains(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("claimName"));
  }

  @Test
  void shouldRejectClaimNameExceedingMaxLengthOnUpdate() {
    // given:
    final String longClaimName = "a".repeat(ValidationConstants.MAX_FIELD_LENGTH + 1);

    // when:
    final List<String> violations =
        VALIDATOR.validateUpdateRequest(longClaimName, "user@example.com", "My Rule");

    // then:
    assertThat(violations)
        .contains(
            ERROR_MESSAGE_TOO_MANY_CHARACTERS.formatted(
                longClaimName, ValidationConstants.MAX_FIELD_LENGTH));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void shouldRejectMissingClaimValueOnUpdate(final String claimValue) {
    // when:
    final List<String> violations = VALIDATOR.validateUpdateRequest("email", claimValue, "My Rule");

    // then:
    assertThat(violations).contains(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("claimValue"));
  }

  @Test
  void shouldRejectClaimValueExceedingMaxLengthOnUpdate() {
    // given:
    final String longClaimValue = "a".repeat(ValidationConstants.MAX_FIELD_LENGTH + 1);

    // when:
    final List<String> violations =
        VALIDATOR.validateUpdateRequest("email", longClaimValue, "My Rule");

    // then:
    assertThat(violations)
        .contains(
            ERROR_MESSAGE_TOO_MANY_CHARACTERS.formatted(
                longClaimValue, ValidationConstants.MAX_FIELD_LENGTH));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void shouldRejectMissingNameOnUpdate(final String name) {
    // when:
    final List<String> violations =
        VALIDATOR.validateUpdateRequest("email", "user@example.com", name);

    // then:
    assertThat(violations).contains(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("name"));
  }

  @Test
  void shouldRejectNameExceedingMaxLengthOnUpdate() {
    // given:
    final String longName = "a".repeat(ValidationConstants.MAX_FIELD_LENGTH + 1);

    // when:
    final List<String> violations =
        VALIDATOR.validateUpdateRequest("email", "user@example.com", longName);

    // then:
    assertThat(violations)
        .contains(
            ERROR_MESSAGE_TOO_MANY_CHARACTERS.formatted(
                longName, ValidationConstants.MAX_FIELD_LENGTH));
  }

  @Test
  void shouldAcceptNameAtMaxLengthOnUpdate() {
    // given:
    final String maxName = "a".repeat(ValidationConstants.MAX_FIELD_LENGTH);

    // when:
    final List<String> violations =
        VALIDATOR.validateUpdateRequest("email", "user@example.com", maxName);

    // then:
    assertThat(violations).isEmpty();
  }

  @Test
  void shouldReturnAllViolationsWhenAllFieldsMissingOnUpdate() {
    // when:
    final List<String> violations = VALIDATOR.validateUpdateRequest(null, null, null);

    // then:
    assertThat(violations)
        .containsExactlyInAnyOrder(
            ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("claimName"),
            ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("claimValue"),
            ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("name"));
  }
}
