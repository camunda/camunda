/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.util.Either;
import java.time.InstantSource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.agrona.DirectBuffer;
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

  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  class EvaluateArrayOfStringsExpressionTest {

    @ParameterizedTest
    @MethodSource("arrayOfStringsExpressions")
    void testSuccessfulEvaluations(final String expression, final List<String> expected) {
      final var processor = new ExpressionProcessor(EXPRESSION_LANGUAGE);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression(expression);
      assertThat(processor.evaluateArrayOfStringsExpression(parsedExpression, -1L))
          .isRight()
          .extracting(Either::get)
          .isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("notArrayOfStringsExpressions")
    void testFailingEvaluations(final String expression, final String message) {
      final var processor = new ExpressionProcessor(EXPRESSION_LANGUAGE);
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

  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  class EvaluationWarningsTest {

    @Test
    void testStringExpression() {
      final var processor = new ExpressionProcessor(EXPRESSION_LANGUAGE);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression("=x");
      assertThat(processor.evaluateStringExpression(parsedExpression, -1L))
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
      final var processor = new ExpressionProcessor(EXPRESSION_LANGUAGE);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression("=x");
      assertThat(processor.evaluateLongExpression(parsedExpression, -1L))
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
      final var processor = new ExpressionProcessor(EXPRESSION_LANGUAGE);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression("=x");
      assertThat(processor.evaluateBooleanExpression(parsedExpression, -1L))
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
      final var processor = new ExpressionProcessor(EXPRESSION_LANGUAGE);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression("=x");
      assertThat(processor.evaluateIntervalExpression(parsedExpression, -1L))
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
      final var processor = new ExpressionProcessor(EXPRESSION_LANGUAGE);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression("=x");
      assertThat(processor.evaluateDateTimeExpression(parsedExpression, -1L))
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
      final var processor = new ExpressionProcessor(EXPRESSION_LANGUAGE);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression("=x");
      assertThat(processor.evaluateArrayExpression(parsedExpression, -1L))
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
      final var processor = new ExpressionProcessor(EXPRESSION_LANGUAGE);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression("=[x]");
      assertThat(processor.evaluateArrayOfStringsExpression(parsedExpression, -1L))
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
      final var processor = new ExpressionProcessor(EXPRESSION_LANGUAGE);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression("=x");
      assertThat(processor.evaluateMessageCorrelationKeyExpression(parsedExpression, -1L))
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
      final var processor = new ExpressionProcessor(EXPRESSION_LANGUAGE);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression("=x");
      assertThat(processor.evaluateVariableMappingExpression(parsedExpression, -1L))
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
  class EvaluationContextTest {

    @Test
    void testNoEvaluationContext() {
      final Expression expression = EXPRESSION_LANGUAGE.parseExpression("=x");
      final var processor = new ExpressionProcessor(EXPRESSION_LANGUAGE);

      final var result = processor.evaluateStringExpression(expression, -1L);

      assertThat(result).isLeft();
    }

    @Test
    void testSimpleEvaluationContext() {
      final Expression expression = EXPRESSION_LANGUAGE.parseExpression("=x");
      final Map<String, DirectBuffer> variables = Map.of("x", MsgPackUtil.asMsgPack("\"foo\""));
      final var processor = new ExpressionProcessor(EXPRESSION_LANGUAGE, variables::get);

      final var result = processor.evaluateStringExpression(expression, -1L);

      assertThat(result).isRight().right().isEqualTo("foo");
    }

    @Test
    void testEnvEvaluationContext() {
      final var envVariable = System.getenv().keySet().stream().findFirst().get();
      final Expression expression =
          EXPRESSION_LANGUAGE.parseExpression(String.format("=camunda.env.%s", envVariable));

      final EnvVariableEvaluationContext envContext =
          new EnvVariableEvaluationContext(Collections.emptyList());

      final var contextEnvNamespace = NamespacedContext.create().register("env", envContext);
      final var camundaNamespace =
          NamespacedContext.create().register("camunda", contextEnvNamespace);

      final var processor = new ExpressionProcessor(EXPRESSION_LANGUAGE, camundaNamespace);
      final var result = processor.evaluateStringExpression(expression, -1L);

      assertThat(result).isRight().right().isEqualTo(System.getenv(envVariable));
    }

    @Test
    void testWithPrimaryContext() {
      final Expression expression = EXPRESSION_LANGUAGE.parseExpression("=x");
      final Map<String, DirectBuffer> variables1 = Map.of("x", MsgPackUtil.asMsgPack("\"foo\""));
      final Map<String, DirectBuffer> variables2 = Map.of("x", MsgPackUtil.asMsgPack("\"bar\""));

      final var processor =
          new ExpressionProcessor(EXPRESSION_LANGUAGE, variables1::get)
              .withPrimaryContext(variables2::get);

      final var result = processor.evaluateStringExpression(expression, -1L);

      assertThat(result).isRight().right().isEqualTo("bar");
    }

    @Test
    void testIt() {
      final var envVariable = System.getenv().keySet().stream().findFirst().get();
      final Expression expression =
          EXPRESSION_LANGUAGE.parseExpression(String.format("=%s", envVariable));

      final EnvVariableEvaluationContext context =
          new EnvVariableEvaluationContext(Collections.emptyList());
      final var processor = new ExpressionProcessor(EXPRESSION_LANGUAGE, context);
      final var result = processor.evaluateStringExpression(expression, -1L);

      assertThat(result).isRight().right().isEqualTo(System.getenv(envVariable));
    }
  }
}
