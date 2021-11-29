/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.el.impl.StaticExpression;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ZeebeExpressionValidatorTest {

  @Nested
  @DisplayName("ZeebeExpressionValidator.isListOfCsv(Expression)")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class IsListOfValues {

    @ParameterizedTest
    @MethodSource("csv")
    void shouldAcceptCsv(final String value) {
      final var expression = new StaticExpression(value);
      assertThat(ZeebeExpressionValidator.isListOfCsv(expression))
          .describedAs("\"%s\" is CSV", value)
          .isTrue();
    }

    @ParameterizedTest
    @MethodSource("notCsv")
    void shouldRejectNotCsv(final String value) {
      final var expression = new StaticExpression(value);
      assertThat(ZeebeExpressionValidator.isListOfCsv(expression))
          .describedAs("\"%s\" is not CSV", value)
          .isFalse();
    }

    Stream<Arguments> csv() {
      return Stream.of(
          Arguments.of(""),
          Arguments.of("a"),
          Arguments.of("a,b"),
          Arguments.of("a,b,c"),
          Arguments.of("\"abcd\",\"efgh\",\"ijkl\""),
          Arguments.of("1"),
          Arguments.of("1,2"),
          Arguments.of("1,a,3"),
          Arguments.of(" a"),
          Arguments.of("a "),
          Arguments.of(" a "),
          Arguments.of(" a,b "),
          Arguments.of(" a ,b "),
          Arguments.of(" a, b "),
          Arguments.of(" a , b "));
    }

    Stream<Arguments> notCsv() {
      return Stream.of(
          Arguments.of(","),
          Arguments.of("a,"),
          Arguments.of(",b"),
          Arguments.of(",b,"),
          Arguments.of("a,,c"),
          Arguments.of("a, ,c"),
          Arguments.of(" ,"),
          Arguments.of(", "),
          Arguments.of(" , "));
    }
  }
}
