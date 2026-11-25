/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.protocol.model.UserRequest;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.security.validation.UserValidator;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UserRequestValidatorTest {

  private static final Pattern PERMISSIVE_ID_PATTERN = Pattern.compile("^[a-z0-9/%?;#.]+$");

  private final UserRequestValidator validator =
      new UserRequestValidator(
          new UserValidator(new IdentifierValidator(PERMISSIVE_ID_PATTERN, PERMISSIVE_ID_PATTERN)));

  @ParameterizedTest
  @ValueSource(strings = {".", "..", "qa%b", "qa;b", "%", ";"})
  void shouldRejectUsernameThatCannotBeAddressed(final String username) {
    // given
    final var request = userRequestWith(username);

    // when
    final var result = validator.validateCreateRequest(request);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail())
        .isEqualTo(
            UserRequestValidator.ERROR_MESSAGE_UNADDRESSABLE_USERNAME.formatted(username) + ".");
  }

  @ParameterizedTest
  @ValueSource(strings = {"qa.", "qa/b", "qa?b", "qa#b", ".qa", "qa..b"})
  void shouldAcceptUsernameThatCanBeAddressed(final String username) {
    // given
    final var request = userRequestWith(username);

    // when
    final var result = validator.validateCreateRequest(request);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldKeepPatternViolationWhenUsernameDoesNotMatchPattern() {
    // given
    final var request = userRequestWith("QA%B");

    // when
    final var result = validator.validateCreateRequest(request);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail())
        .contains("contains illegal characters")
        .doesNotContain("cannot be addressed");
  }

  private static UserRequest userRequestWith(final String username) {
    return UserRequest.Builder.create()
        .username(username)
        .password("zabraboof")
        .name("Foo Bar")
        .email("bar@baz.com")
        .build();
  }
}
