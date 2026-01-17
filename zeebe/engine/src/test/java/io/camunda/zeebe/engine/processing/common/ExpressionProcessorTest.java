/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.common;

import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.expression.InMemoryVariableEvaluationContext;
import io.camunda.zeebe.engine.processing.expression.ScopedEvaluationContext;
import io.camunda.zeebe.util.Either;
import java.time.InstantSource;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ExpressionProcessorTest {

  private static final ExpressionLanguage EXPRESSION_LANGUAGE =
      ExpressionLanguageFactory.createExpressionLanguage(
          new ZeebeFeelEngineClock(InstantSource.system()));
  private static final ScopedEvaluationContext DEFAULT_CONTEXT_LOOKUP =
      variableName -> Either.left(null);

  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  class EvaluateArrayOfStringsExpressionTest {

    @ParameterizedTest
    @MethodSource("arrayOfStringsExpressions")
    void testSuccessfulEvaluations(final String expression, final List<String> expected) {
      final var processor = new ExpressionProcessor(EXPRESSION_LANGUAGE, DEFAULT_CONTEXT_LOOKUP);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression(expression);
      assertThat(processor.evaluateArrayOfStringsExpression(parsedExpression, -1L, "tenant_1"))
          .isRight()
          .extracting(Either::get)
          .isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("notArrayOfStringsExpressions")
    void testFailingEvaluations(final String expression, final String message) {
      final var processor = new ExpressionProcessor(EXPRESSION_LANGUAGE, DEFAULT_CONTEXT_LOOKUP);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression(expression);
      assertThat(processor.evaluateArrayOfStringsExpression(parsedExpression, -1L, "tenant_1"))
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

  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  class EvaluationWarningsTest {

    @Test
    void testStringExpression() {
      final var processor = new ExpressionProcessor(EXPRESSION_LANGUAGE, DEFAULT_CONTEXT_LOOKUP);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression("=x");
      assertThat(processor.evaluateStringExpression(parsedExpression, -1L, "tenant_1"))
          .isLeft()
          .extracting(r -> r.getLeft().getMessage())
          .isEqualTo(
              """
              Expected result of the expression 'x' to be 'STRING', but was 'NULL'. \
              The evaluation reported the following warnings:
              [NO_VARIABLE_FOUND] No variable found with name 'x'""");
    }

    @Test
    void testLongExpression() {
      final var processor = new ExpressionProcessor(EXPRESSION_LANGUAGE, DEFAULT_CONTEXT_LOOKUP);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression("=x");
      assertThat(processor.evaluateLongExpression(parsedExpression, -1L, "tenant_1"))
          .isLeft()
          .extracting(r -> r.getLeft().getMessage())
          .isEqualTo(
              """
              Expected result of the expression 'x' to be 'NUMBER', but was 'NULL'. \
              The evaluation reported the following warnings:
              [NO_VARIABLE_FOUND] No variable found with name 'x'""");
    }

    @Test
    void testBooleanExpression() {
      final var processor = new ExpressionProcessor(EXPRESSION_LANGUAGE, DEFAULT_CONTEXT_LOOKUP);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression("=x");
      assertThat(processor.evaluateBooleanExpression(parsedExpression, -1L, "tenant_1"))
          .isLeft()
          .extracting(r -> r.getLeft().getMessage())
          .isEqualTo(
              """
              Expected result of the expression 'x' to be 'BOOLEAN', but was 'NULL'. \
              The evaluation reported the following warnings:
              [NO_VARIABLE_FOUND] No variable found with name 'x'""");
    }

    @Test
    void testIntervalExpression() {
      final var processor = new ExpressionProcessor(EXPRESSION_LANGUAGE, DEFAULT_CONTEXT_LOOKUP);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression("=x");
      assertThat(processor.evaluateIntervalExpression(parsedExpression, -1L, "tenant_1"))
          .isLeft()
          .extracting(r -> r.getLeft().getMessage())
          .isEqualTo(
              """
              Expected result of the expression 'x' to be one of '[DURATION, PERIOD, STRING]', but was 'NULL'. \
              The evaluation reported the following warnings:
              [NO_VARIABLE_FOUND] No variable found with name 'x'""");
    }

    @Test
    void testDateTimeExpression() {
      final var processor = new ExpressionProcessor(EXPRESSION_LANGUAGE, DEFAULT_CONTEXT_LOOKUP);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression("=x");
      assertThat(processor.evaluateDateTimeExpression(parsedExpression, -1L, "tenant_1"))
          .isLeft()
          .extracting(r -> r.getLeft().getMessage())
          .isEqualTo(
              """
              Expected result of the expression 'x' to be one of '[DATE_TIME, STRING]', but was 'NULL'. \
              The evaluation reported the following warnings:
              [NO_VARIABLE_FOUND] No variable found with name 'x'""");
    }

    @Test
    void testArrayExpression() {
      final var processor = new ExpressionProcessor(EXPRESSION_LANGUAGE, DEFAULT_CONTEXT_LOOKUP);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression("=x");
      assertThat(processor.evaluateArrayExpression(parsedExpression, -1L, "tenant_1"))
          .isLeft()
          .extracting(r -> r.getLeft().getMessage())
          .isEqualTo(
              """
              Expected result of the expression 'x' to be 'ARRAY', but was 'NULL'. \
              The evaluation reported the following warnings:
              [NO_VARIABLE_FOUND] No variable found with name 'x'""");
    }

    @Test
    void testStringArrayExpression() {
      final var processor = new ExpressionProcessor(EXPRESSION_LANGUAGE, DEFAULT_CONTEXT_LOOKUP);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression("=[x]");
      assertThat(processor.evaluateArrayOfStringsExpression(parsedExpression, -1L, "tenant_1"))
          .isLeft()
          .extracting(r -> r.getLeft().getMessage())
          .isEqualTo(
              """
              Expected result of the expression '[x]' to be 'ARRAY' containing 'STRING' items, \
              but was 'ARRAY' containing at least one non-'STRING' item. \
              The evaluation reported the following warnings:
              [NO_VARIABLE_FOUND] No variable found with name 'x'""");
    }

    @Test
    void testMessageCorrelationKeyExpression() {
      final var processor = new ExpressionProcessor(EXPRESSION_LANGUAGE, DEFAULT_CONTEXT_LOOKUP);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression("=x");
      assertThat(
              processor.evaluateMessageCorrelationKeyExpression(parsedExpression, -1L, "tenant_1"))
          .isLeft()
          .extracting(r -> r.getLeft().getMessage())
          .isEqualTo(
              """
              Failed to extract the correlation key for 'x': \
              The value must be either a string or a number, but was 'NULL'. \
              The evaluation reported the following warnings:
              [NO_VARIABLE_FOUND] No variable found with name 'x'""");
    }

    @Test
    void testVariableMappingExpression() {
      final var processor = new ExpressionProcessor(EXPRESSION_LANGUAGE, DEFAULT_CONTEXT_LOOKUP);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression("=x");
      assertThat(processor.evaluateVariableMappingExpression(parsedExpression, -1L, "tenant_1"))
          .isLeft()
          .extracting(r -> r.getLeft().getMessage())
          .isEqualTo(
              """
              Expected result of the expression 'x' to be 'OBJECT', but was 'NULL'. \
              The evaluation reported the following warnings:
              [NO_VARIABLE_FOUND] No variable found with name 'x'""");
    }
  }

  @Nested
  class EvaluateBooleanExpressionWithStringTest {

    @Test
    void shouldEvaluateValidBooleanExpression() {
      // given
      final var processor = new ExpressionProcessor(EXPRESSION_LANGUAGE, DEFAULT_CONTEXT_LOOKUP);
      final var context = new InMemoryVariableEvaluationContext(java.util.Map.of("x", 10, "y", 5));

      // when
      final var result = processor.evaluateBooleanExpression("= x > y", context);

      // then
      assertThat(result).isRight().extracting(Either::get).isEqualTo(true);
    }

    @Test
    void shouldReturnFailureForInvalidExpression() {
      // given
      final var processor = new ExpressionProcessor(EXPRESSION_LANGUAGE, DEFAULT_CONTEXT_LOOKUP);
      final var context = new InMemoryVariableEvaluationContext(java.util.Map.of());

      // when
      final var result = processor.evaluateBooleanExpression("= x >", context);

      // then
      assertThat(result)
          .isLeft()
          .extracting(r -> r.getLeft().getMessage())
          .asString()
          .startsWith("failed to parse expression ' x >':");
    }
  }
}
