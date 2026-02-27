/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.validation;

import static io.camunda.security.validation.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.security.validation.ErrorMessages.ERROR_MESSAGE_INVALID_EMAIL;
import static io.camunda.security.validation.ErrorMessages.ERROR_MESSAGE_TOO_MANY_CHARACTERS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UserValidatorTest {

  private static final UserValidator VALIDATOR =
      new UserValidator(
          new IdentifierValidator(
              Pattern.compile("^[a-zA-Z0-9_~@.+-]+$"), Pattern.compile("^[a-zA-Z0-9_~@.+-]+$")));

  // ---- validateCreateRequest ----

  @Test
  void shouldReturnNoViolationsForValidCreateRequest() {
    final List<String> violations =
        VALIDATOR.validateCreateRequest("alice", "s3cr3t", "Alice Smith", "alice@example.com");

    assertThat(violations).isEmpty();
  }

  @Test
  void shouldReturnNoViolationsForCreateRequestWithoutOptionalFields() {
    final List<String> violations = VALIDATOR.validateCreateRequest("alice", "s3cr3t", null, null);

    assertThat(violations).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void shouldRejectMissingUsername(final String username) {
    final List<String> violations = VALIDATOR.validateCreateRequest(username, "s3cr3t", null, null);

    assertThat(violations).contains(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("username"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void shouldRejectMissingPassword(final String password) {
    final List<String> violations = VALIDATOR.validateCreateRequest("alice", password, null, null);

    assertThat(violations).contains(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("password"));
  }

  @Test
  void shouldRejectAllMissingMandatoryFieldsOnCreate() {
    final List<String> violations = VALIDATOR.validateCreateRequest(null, null, null, null);

    assertThat(violations)
        .containsExactlyInAnyOrder(
            ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("username"),
            ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("password"));
  }

  @Test
  void shouldRejectInvalidEmailOnCreate() {
    final String invalidEmail = "not-an-email";
    final List<String> violations =
        VALIDATOR.validateCreateRequest("alice", "s3cr3t", null, invalidEmail);

    assertThat(violations).contains(ERROR_MESSAGE_INVALID_EMAIL.formatted(invalidEmail));
  }

  @Test
  void shouldAcceptBlankEmailOnCreate() {
    final List<String> violations = VALIDATOR.validateCreateRequest("alice", "s3cr3t", null, "   ");

    assertThat(violations).isEmpty();
  }

  @Test
  void shouldRejectEmailExceedingMaxLengthOnCreate() {
    final String longEmail = "a".repeat(250) + "@example.com";
    final List<String> violations =
        VALIDATOR.validateCreateRequest("alice", "s3cr3t", null, longEmail);

    assertThat(violations)
        .contains(
            ERROR_MESSAGE_TOO_MANY_CHARACTERS.formatted(
                longEmail, ValidationConstants.MAX_FIELD_LENGTH));
  }

  @Test
  void shouldRejectNameExceedingMaxLengthOnCreate() {
    final String longName = "a".repeat(ValidationConstants.MAX_FIELD_LENGTH + 1);
    final List<String> violations =
        VALIDATOR.validateCreateRequest("alice", "s3cr3t", longName, null);

    assertThat(violations)
        .contains(
            ERROR_MESSAGE_TOO_MANY_CHARACTERS.formatted(
                longName, ValidationConstants.MAX_FIELD_LENGTH));
  }

  @Test
  void shouldAcceptNameAtMaxLengthOnCreate() {
    final String maxName = "a".repeat(ValidationConstants.MAX_FIELD_LENGTH);
    final List<String> violations =
        VALIDATOR.validateCreateRequest("alice", "s3cr3t", maxName, null);

    assertThat(violations).isEmpty();
  }

  @Test
  void shouldAcceptBlankNameOnCreate() {
    final List<String> violations = VALIDATOR.validateCreateRequest("alice", "s3cr3t", "   ", null);

    assertThat(violations).isEmpty();
  }

  // ---- validateUpdateRequest ----

  @Test
  void shouldReturnNoViolationsForValidUpdateRequest() {
    final List<String> violations =
        VALIDATOR.validateUpdateRequest("Alice Smith", "alice@example.com");

    assertThat(violations).isEmpty();
  }

  @Test
  void shouldReturnNoViolationsForUpdateRequestWithNullFields() {
    final List<String> violations = VALIDATOR.validateUpdateRequest(null, null);

    assertThat(violations).isEmpty();
  }

  @Test
  void shouldRejectInvalidEmailOnUpdate() {
    final String invalidEmail = "not-an-email";
    final List<String> violations = VALIDATOR.validateUpdateRequest(null, invalidEmail);

    assertThat(violations).contains(ERROR_MESSAGE_INVALID_EMAIL.formatted(invalidEmail));
  }

  @Test
  void shouldAcceptBlankEmailOnUpdate() {
    final List<String> violations = VALIDATOR.validateUpdateRequest(null, "   ");

    assertThat(violations).isEmpty();
  }

  @Test
  void shouldRejectEmailExceedingMaxLengthOnUpdate() {
    final String longEmail = "a".repeat(250) + "@example.com";
    final List<String> violations = VALIDATOR.validateUpdateRequest(null, longEmail);

    assertThat(violations)
        .contains(
            ERROR_MESSAGE_TOO_MANY_CHARACTERS.formatted(
                longEmail, ValidationConstants.MAX_FIELD_LENGTH));
  }

  @Test
  void shouldRejectNameExceedingMaxLengthOnUpdate() {
    final String longName = "a".repeat(ValidationConstants.MAX_FIELD_LENGTH + 1);
    final List<String> violations = VALIDATOR.validateUpdateRequest(longName, null);

    assertThat(violations)
        .contains(
            ERROR_MESSAGE_TOO_MANY_CHARACTERS.formatted(
                longName, ValidationConstants.MAX_FIELD_LENGTH));
  }

  @Test
  void shouldAcceptNameAtMaxLengthOnUpdate() {
    final String maxName = "a".repeat(ValidationConstants.MAX_FIELD_LENGTH);
    final List<String> violations = VALIDATOR.validateUpdateRequest(maxName, null);

    assertThat(violations).isEmpty();
  }

  @Test
  void shouldAcceptBlankNameOnUpdate() {
    final List<String> violations = VALIDATOR.validateUpdateRequest("   ", null);

    assertThat(violations).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"user@example.com", "user+tag@sub.domain.org", "first.last@company.co.uk"})
  void shouldAcceptValidEmailFormats(final String email) {
    final List<String> violations = VALIDATOR.validateUpdateRequest(null, email);

    assertThat(violations).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"plainaddress", "@missinglocal.com", "missing@domain"})
  void shouldRejectInvalidEmailFormats(final String email) {
    final List<String> violations = VALIDATOR.validateUpdateRequest(null, email);

    assertThat(violations).contains(ERROR_MESSAGE_INVALID_EMAIL.formatted(email));
  }
}
