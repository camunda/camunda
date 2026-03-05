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

import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class GroupValidatorTest {

  private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_~@.+-]+$");

  private static final GroupValidator VALIDATOR =
      new GroupValidator(new IdentifierValidator(ID_PATTERN, ID_PATTERN));

  // ---- validate ----

  @Test
  void shouldReturnNoViolationsForValidGroup() {
    // when:
    final List<String> violations = VALIDATOR.validate("my-group", "My Group");

    // then:
    assertThat(violations).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void shouldRejectMissingGroupId(final String groupId) {
    // when:
    final List<String> violations = VALIDATOR.validate(groupId, "My Group");

    // then:
    assertThat(violations).contains(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("groupId"));
  }

  @Test
  void shouldRejectGroupIdWithIllegalCharacters() {
    // when:
    final List<String> violations = VALIDATOR.validate("invalid id!", "My Group");

    // then:
    assertThat(violations)
        .contains(ERROR_MESSAGE_ILLEGAL_CHARACTER.formatted("groupId", ID_PATTERN));
  }

  @Test
  void shouldRejectGroupIdExceedingMaxLength() {
    // given:
    final String longId = "a".repeat(ValidationConstants.MAX_FIELD_LENGTH + 1);

    // when:
    final List<String> violations = VALIDATOR.validate(longId, "My Group");

    // then:
    assertThat(violations)
        .contains(
            ERROR_MESSAGE_TOO_MANY_CHARACTERS.formatted(
                "groupId", ValidationConstants.MAX_FIELD_LENGTH));
  }

  @Test
  void shouldAcceptGroupIdAtMaxLength() {
    // given:
    final String maxId = "a".repeat(ValidationConstants.MAX_FIELD_LENGTH);

    // when:
    final List<String> violations = VALIDATOR.validate(maxId, "My Group");

    // then:
    assertThat(violations).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void shouldRejectMissingName(final String name) {
    // when:
    final List<String> violations = VALIDATOR.validate("my-group", name);

    // then:
    assertThat(violations).contains(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("name"));
  }

  @Test
  void shouldRejectNameExceedingMaxLength() {
    // given:
    final String longName = "a".repeat(ValidationConstants.MAX_FIELD_LENGTH + 1);

    // when:
    final List<String> violations = VALIDATOR.validate("my-group", longName);

    // then:
    assertThat(violations)
        .contains(
            ERROR_MESSAGE_TOO_MANY_CHARACTERS.formatted(
                "name", ValidationConstants.MAX_FIELD_LENGTH));
  }

  @Test
  void shouldAcceptNameAtMaxLength() {
    // given:
    final String maxName = "a".repeat(ValidationConstants.MAX_FIELD_LENGTH);

    // when:
    final List<String> violations = VALIDATOR.validate("my-group", maxName);

    // then:
    assertThat(violations).isEmpty();
  }

  @Test
  void shouldReturnAllViolationsWhenBothFieldsInvalid() {
    // when:
    final List<String> violations = VALIDATOR.validate(null, null);

    // then:
    assertThat(violations)
        .containsExactlyInAnyOrder(
            ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("groupId"),
            ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("name"));
  }

  // ---- validateMembers ----

  @Test
  void shouldReturnNoViolationsForNullMemberList() {
    // when:
    final List<String> violations = VALIDATOR.validateMembers(null, EntityType.USER);

    // then:
    assertThat(violations).isEmpty();
  }

  @Test
  void shouldReturnNoViolationsForValidUserMembers() {
    // when:
    final List<String> violations =
        VALIDATOR.validateMembers(List.of("alice", "bob"), EntityType.USER);

    // then:
    assertThat(violations).isEmpty();
  }

  @Test
  void shouldRejectMemberIdWithIllegalCharacters() {
    // when:
    final List<String> violations =
        VALIDATOR.validateMembers(List.of("valid", "invalid id!"), EntityType.USER);

    // then:
    assertThat(violations)
        .contains(ERROR_MESSAGE_ILLEGAL_CHARACTER.formatted("username", ID_PATTERN));
  }

  @Test
  void shouldRejectBlankMemberIds() {
    // when:
    final List<String> violations = VALIDATOR.validateMembers(List.of(""), EntityType.USER);

    // then:
    assertThat(violations).contains(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("username"));
  }

  // ---- validateMember ----

  @Test
  void shouldReturnNoViolationsForValidMember() {
    // when:
    final List<String> violations = VALIDATOR.validateMember("my-group", "alice", EntityType.USER);

    // then:
    assertThat(violations).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void shouldRejectMissingGroupIdInValidateMember(final String groupId) {
    // when:
    final List<String> violations = VALIDATOR.validateMember(groupId, "alice", EntityType.USER);

    // then:
    assertThat(violations).contains(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("groupId"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void shouldRejectMissingMemberIdInValidateMember(final String memberId) {
    // when:
    final List<String> violations = VALIDATOR.validateMember("my-group", memberId, EntityType.USER);

    // then:
    assertThat(violations).contains(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("username"));
  }

  @Test
  void shouldReturnAllViolationsWhenBothMemberFieldsInvalid() {
    // when:
    final List<String> violations = VALIDATOR.validateMember(null, null, EntityType.USER);

    // then:
    assertThat(violations)
        .containsExactlyInAnyOrder(
            ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("groupId"),
            ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("username"));
  }
}
