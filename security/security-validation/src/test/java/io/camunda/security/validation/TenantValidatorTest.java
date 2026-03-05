/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.validation;

import static io.camunda.security.validation.ErrorMessages.ERROR_MESSAGE_TOO_MANY_CHARACTERS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TenantValidatorTest {

  private static final TenantValidator VALIDATOR =
      new TenantValidator(
          new IdentifierValidator(
              Pattern.compile("^[a-zA-Z0-9_~@.+-]+$"), Pattern.compile("^[a-zA-Z0-9_~@.+-]+$")));

  @Test
  public void shouldValidateMandatoryFields() {
    // when:
    final List<String> violations = VALIDATOR.validateCreate(null, "");

    // then:
    assertThat(violations).containsExactlyInAnyOrder("No tenantId provided", "No name provided");
  }

  @Test
  public void shouldSuccessfullyConfigure() {
    // when:
    final List<String> violations = VALIDATOR.validateCreate("foo", "Foo");

    // then:
    assertThat(violations).isEmpty();
  }

  @Test
  void shouldRejectNameExceedingMaxLengthOnCreate() {
    // given:
    final String longName = "a".repeat(ValidationConstants.MAX_FIELD_LENGTH + 1);

    // when:
    final List<String> violations = VALIDATOR.validateCreate("foo", longName);

    // then:
    assertThat(violations)
        .containsExactly(
            ERROR_MESSAGE_TOO_MANY_CHARACTERS.formatted(
                "name", ValidationConstants.MAX_FIELD_LENGTH));
  }

  @Test
  void shouldAcceptNameAtMaxLengthOnCreate() {
    // given:
    final String maxName = "a".repeat(ValidationConstants.MAX_FIELD_LENGTH);

    // when:
    final List<String> violations = VALIDATOR.validateCreate("foo", maxName);

    // then:
    assertThat(violations).isEmpty();
  }

  @Test
  void shouldRejectNameExceedingMaxLengthOnUpdate() {
    // given:
    final String longName = "a".repeat(ValidationConstants.MAX_FIELD_LENGTH + 1);

    // when:
    final List<String> violations = VALIDATOR.validateUpdate(longName);

    // then:
    assertThat(violations)
        .containsExactly(
            ERROR_MESSAGE_TOO_MANY_CHARACTERS.formatted(
                "name", ValidationConstants.MAX_FIELD_LENGTH));
  }

  @Test
  void shouldAcceptNameAtMaxLengthOnUpdate() {
    // given:
    final String maxName = "a".repeat(ValidationConstants.MAX_FIELD_LENGTH);

    // when:
    final List<String> violations = VALIDATOR.validateUpdate(maxName);

    // then:
    assertThat(violations).isEmpty();
  }
}
