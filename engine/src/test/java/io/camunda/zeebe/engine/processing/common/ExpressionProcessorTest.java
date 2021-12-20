/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.common;

import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor.VariablesLookup;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ExpressionProcessorTest {

  private static final ExpressionLanguage EXPRESSION_LANGUAGE =
      ExpressionLanguageFactory.createExpressionLanguage();
  private static final VariablesLookup EMPTY_LOOKUP = (x, y) -> null;

  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  class EvaluateArrayOfStringsExpressionTest {

    @ParameterizedTest
    @MethodSource("arrayOfStringsExpressions")
    void testSuccessfulEvaluations(final String expression, final List<String> expected) {
      final var processor = new ExpressionProcessor(EXPRESSION_LANGUAGE, EMPTY_LOOKUP);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression(expression);
      assertThat(processor.evaluateArrayOfStringsExpression(parsedExpression, -1L))
          .isRight()
          .extracting(Either::get)
          .isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("notArrayOfStringsExpressions")
    void testFailingEvaluations(final String expression, final String message) {
      final var processor = new ExpressionProcessor(EXPRESSION_LANGUAGE, EMPTY_LOOKUP);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression(expression);
      assertThat(processor.evaluateArrayOfStringsExpression(parsedExpression, -1L))
          .isLeft()
          .extracting(Either::getLeft)
          .extracting(Failure::getMessage)
          .isEqualTo(message);
    }

    Stream<Arguments> arrayOfStringsExpressions() {
      return Stream.of(
          Arguments.of("= []", List.of()),
          Arguments.of("= [\"a\"]", List.of("a")),
          Arguments.of("= [\"a\",\"b\"]", List.of("a", "b")));
    }

    Stream<Arguments> notArrayOfStringsExpressions() {
      return Stream.of(
          Arguments.of(
              "= \"a\"",
              "Expected result of the expression ' \"a\"' to be 'ARRAY', but was 'STRING'."),
          Arguments.of(
              "= 1", "Expected result of the expression ' 1' to be 'ARRAY', but was 'NUMBER'."),
          Arguments.of(
              "= {}", "Expected result of the expression ' {}' to be 'ARRAY', but was 'OBJECT'."),
          Arguments.of(
              "[]", "Expected result of the expression '[]' to be 'ARRAY', but was 'STRING'."),
          Arguments.of(
              "= [1,2,3]",
              "Expected result of the expression ' [1,2,3]' to be 'ARRAY' containing 'STRING' items, but was 'ARRAY' containing at least one non-'STRING' item."),
          Arguments.of(
              "= [{},{}]",
              "Expected result of the expression ' [{},{}]' to be 'ARRAY' containing 'STRING' items, but was 'ARRAY' containing at least one non-'STRING' item."),
          Arguments.of(
              "= [null]",
              "Expected result of the expression ' [null]' to be 'ARRAY' containing 'STRING' items, but was 'ARRAY' containing at least one non-'STRING' item."));
    }
  }
}
