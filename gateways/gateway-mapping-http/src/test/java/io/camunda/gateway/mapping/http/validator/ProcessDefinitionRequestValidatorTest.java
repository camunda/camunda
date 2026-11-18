/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProcessDefinitionRequestValidatorTest {

  @Test
  void shouldAcceptValidKey() {
    // when
    final var result =
        ProcessDefinitionRequestValidator.validateSuspensionByKey("2251799813685249");

    // then
    assertThat(result).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "12.34", "12abc", "", " "})
  void shouldRejectInvalidKey(final String invalidKey) {
    // when
    final var result = ProcessDefinitionRequestValidator.validateSuspensionByKey(invalidKey);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail()).contains("processDefinitionKey", "is not a valid key");
  }

  @Test
  void shouldAcceptIdWithoutVersion() {
    // when
    final var result = ProcessDefinitionRequestValidator.validateSuspensionById("invoice", null);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldAcceptIdWithPositiveVersion() {
    // when
    final var result = ProcessDefinitionRequestValidator.validateSuspensionById("invoice", 1);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldRejectIllegalId() {
    // when
    final var result =
        ProcessDefinitionRequestValidator.validateSuspensionById("1 invalid id", null);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail()).contains("processDefinitionId");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void shouldRejectNonPositiveVersion(final int version) {
    // when
    final var result = ProcessDefinitionRequestValidator.validateSuspensionById("invoice", version);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail())
        .contains(
            "The value for processDefinitionVersion is '%d' but must be a positive integer"
                .formatted(version));
  }
}
