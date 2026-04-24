/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("RequestValidator Tests")
class RequestValidatorTest {

  @Test
  @DisplayName("Should accept valid Long string format")
  void shouldAcceptValidLongStringFormat() {
    final List<String> violations = new ArrayList<>();
    RequestValidator.validateKeyFormat("123456789", "testField", violations);

    assertThat(violations).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "12.34", "12abc", "", " "})
  @DisplayName("Should reject invalid Long string formats")
  void shouldRejectInvalidLongStringFormats(String invalidKey) {
    final List<String> violations = new ArrayList<>();
    RequestValidator.validateKeyFormat(invalidKey, "testField", violations);

    assertThat(violations).hasSize(1);
    assertThat(violations.get(0)).contains("testField");
    assertThat(violations.get(0)).contains("is not a valid key");
  }

  @Test
  @DisplayName("Should accept null values")
  void shouldAcceptNullValues() {
    final List<String> violations = new ArrayList<>();
    RequestValidator.validateKeyFormat(null, "testField", violations);

    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("Should accept maximum Long value")
  void shouldAcceptMaximumLongValue() {
    final List<String> violations = new ArrayList<>();
    RequestValidator.validateKeyFormat(String.valueOf(Long.MAX_VALUE), "testField", violations);

    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("Should accept minimum Long value")
  void shouldAcceptMinimumLongValue() {
    final List<String> violations = new ArrayList<>();
    RequestValidator.validateKeyFormat(String.valueOf(Long.MIN_VALUE), "testField", violations);

    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("Should reject values larger than Long.MAX_VALUE")
  void shouldRejectValuesLargerThanLongMaxValue() {
    final List<String> violations = new ArrayList<>();
    RequestValidator.validateKeyFormat("99999999999999999999999999999", "testField", violations);

    assertThat(violations).hasSize(1);
    assertThat(violations.get(0)).contains("testField");
    assertThat(violations.get(0)).contains("is not a valid key");
  }

  @Test
  @DisplayName("Should accept zero as valid Long value")
  void shouldAcceptZeroAsValidLongValue() {
    final List<String> violations = new ArrayList<>();
    RequestValidator.validateKeyFormat("0", "testField", violations);

    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("Should accept negative Long values")
  void shouldAcceptNegativeLongValues() {
    final List<String> violations = new ArrayList<>();
    RequestValidator.validateKeyFormat("-123456789", "testField", violations);

    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("Should include field name in error message")
  void shouldIncludeFieldNameInErrorMessage() {
    final List<String> violations = new ArrayList<>();
    RequestValidator.validateKeyFormat("invalid", "customFieldName", violations);

    assertThat(violations).hasSize(1);
    assertThat(violations.get(0)).contains("customFieldName");
    assertThat(violations.get(0)).contains("is not a valid key");
  }

  @Test
  @DisplayName("Should handle leading and trailing whitespace")
  void shouldHandleLeadingAndTrailingWhitespace() {
    final List<String> violations = new ArrayList<>();
    RequestValidator.validateKeyFormat("  123456789  ", "testField", violations);

    // Should reject because KeyUtil.tryParseLong() is strict about format
    assertThat(violations).hasSize(1);
    assertThat(violations.get(0)).contains("testField");
    assertThat(violations.get(0)).contains("is not a valid key");
  }

  // --- validateDecisionEvaluationInstanceKeyFormat tests ---

  @ParameterizedTest
  @ValueSource(strings = {"123-1", "0-1", "2251799813684367-1", "1-99"})
  @DisplayName("Should accept valid decision evaluation instance keys")
  void shouldAcceptValidDecisionEvaluationInstanceKeys(final String validKey) {
    // given
    final List<String> violations = new ArrayList<>();

    // when
    RequestValidator.validateDecisionEvaluationInstanceKeyFormat(validKey, violations);

    // then
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("Should accept null decision evaluation instance key without violations")
  void shouldRejectNullDecisionEvaluationInstanceKey() {
    // given
    final List<String> violations = new ArrayList<>();

    // when
    RequestValidator.validateDecisionEvaluationInstanceKeyFormat(null, violations);

    // then
    assertThat(violations).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "+++", "abc", "123", "-1-2", "1-", "-1", "1--1", "1-2-3", "1-a", "a-1", " 1-1", "1-1 ", ""
      })
  @DisplayName("Should reject invalid decision evaluation instance keys")
  void shouldRejectInvalidDecisionEvaluationInstanceKeys(final String invalidKey) {
    // given
    final List<String> violations = new ArrayList<>();

    // when
    RequestValidator.validateDecisionEvaluationInstanceKeyFormat(invalidKey, violations);

    // then
    assertThat(violations).hasSize(1);
    assertThat(violations.getFirst()).contains("decisionEvaluationInstanceKey");
    assertThat(violations.getFirst()).contains("is not a valid decision evaluation instance key");
  }
}
