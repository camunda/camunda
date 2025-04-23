/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.el;

import static io.camunda.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.camunda.zeebe.el.util.TestFeelEngineClock;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.agrona.DirectBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class FeelFromAiFunctionTest {

  private static final Map<String, DirectBuffer> CONTEXT_VALUES =
      Map.of("toolCall", asMsgPack(Map.of("a", 10, "b", "twenty")));

  private final ExpressionLanguage expressionLanguage =
      ExpressionLanguageFactory.createExpressionLanguage(new TestFeelEngineClock());

  @ParameterizedTest(name = "{0}")
  @MethodSource("valueTestCases")
  <T> void returnsInjectedValue(final FromAiExpressionTestCase<T> testCase) {
    final var evaluationResult =
        evaluateSuccessfulExpression(testCase.expression(), CONTEXT_VALUES::get);
    assertThat(evaluationResult.getType()).isEqualTo(testCase.expectedResultType);
    assertThat(testCase.resultExtractor.apply(evaluationResult)).isEqualTo(testCase.expectedResult);
  }

  static Stream<FromAiExpressionTestCase<?>> valueTestCases() {
    return Stream.of(
        new FeelFromAiFunctionTest.FromAiExpressionTestCase<>(
            "Only value (number)",
            "fromAi(toolCall.a)",
            ResultType.NUMBER,
            EvaluationResult::getNumber,
            10),
        new FeelFromAiFunctionTest.FromAiExpressionTestCase<>(
            "Only value (string)",
            "fromAi(toolCall.b)",
            ResultType.STRING,
            EvaluationResult::getString,
            "twenty"),
        new FeelFromAiFunctionTest.FromAiExpressionTestCase<>(
            "Value & description",
            "fromAi(toolCall.a, \"The a parameter.\")",
            ResultType.NUMBER,
            EvaluationResult::getNumber,
            10),
        new FeelFromAiFunctionTest.FromAiExpressionTestCase<>(
            "Value, description & type",
            "fromAi(toolCall.a, \"The a parameter.\", \"integer\")",
            ResultType.NUMBER,
            EvaluationResult::getNumber,
            10),
        new FeelFromAiFunctionTest.FromAiExpressionTestCase<>(
            "Value, description, type & schema",
            "fromAi(toolCall.a, \"The a parameter.\", \"integer\", { multipleOf: 1 })",
            ResultType.NUMBER,
            EvaluationResult::getNumber,
            10),
        new FeelFromAiFunctionTest.FromAiExpressionTestCase<>(
            "Value, description, type, schema & options",
            "fromAi(toolCall.a, \"The a parameter.\", \"integer\", { multipleOf: 1 }, { optional: true })",
            ResultType.NUMBER,
            EvaluationResult::getNumber,
            10),
        new FeelFromAiFunctionTest.FromAiExpressionTestCase<>(
            "Only value (named params)",
            "fromAi(value: toolCall.a)",
            ResultType.NUMBER,
            EvaluationResult::getNumber,
            10),
        new FeelFromAiFunctionTest.FromAiExpressionTestCase<>(
            "Value & description (named params)",
            "fromAi(value: toolCall.a, description: \"The a parameter.\")",
            ResultType.NUMBER,
            EvaluationResult::getNumber,
            10),
        new FeelFromAiFunctionTest.FromAiExpressionTestCase<>(
            "Value, description & type (named params)",
            "fromAi(value: toolCall.a, description: \"The a parameter.\", type: \"integer\")",
            ResultType.NUMBER,
            EvaluationResult::getNumber,
            10),
        new FeelFromAiFunctionTest.FromAiExpressionTestCase<>(
            "Value, description, type & schema (named params)",
            "fromAi(value: toolCall.a, description: \"The a parameter.\", type: \"integer\", schema: { multipleOf: 1 })",
            ResultType.NUMBER,
            EvaluationResult::getNumber,
            10),
        new FeelFromAiFunctionTest.FromAiExpressionTestCase<>(
            "Value, description, type, schema & options (named params)",
            "fromAi(value: toolCall.a, description: \"The a parameter.\", type: \"integer\", schema: { multipleOf: 1 }, options: { optional: true })",
            ResultType.NUMBER,
            EvaluationResult::getNumber,
            10),
        new FeelFromAiFunctionTest.FromAiExpressionTestCase<>(
            "Value, description, type, schema & options (named params, mixed order)",
            "fromAi(schema: { multipleOf: 1 }, description: \"The a parameter.\", options: { optional: true }, value: toolCall.a, type: \"integer\")",
            ResultType.NUMBER,
            EvaluationResult::getNumber,
            10));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidParameterLists")
  void createsWarningWhenParameterListDoesNotMatch(
      final String expression, final int parameterCount) {
    final var evaluationResult = evaluateSuccessfulExpression(expression, CONTEXT_VALUES::get);

    assertThat(evaluationResult.isFailure()).isFalse();
    assertThat(evaluationResult.getWarnings())
        .extracting(EvaluationWarning::getType, EvaluationWarning::getMessage)
        .containsExactly(
            tuple(
                "NO_FUNCTION_FOUND",
                "No function found with name 'fromAi' and %d parameters"
                    .formatted(parameterCount)));
  }

  public static Stream<Arguments> invalidParameterLists() {
    return Stream.of(
        arguments("fromAi()", 0),
        arguments(
            "fromAi(toolCall.a, \"The a parameter.\", \"integer\", { multipleOf: 1 }, { optional: true }, \"testme\")",
            6));
  }

  @Test
  void returnsNullWhenInputValueIsNull() {
    final var evaluationResult =
        evaluateSuccessfulExpression("fromAi(toolCall.c)", CONTEXT_VALUES::get);
    assertThat(evaluationResult.getType()).isEqualTo(ResultType.NULL);
  }

  private EvaluationResult evaluateSuccessfulExpression(
      final String expression, final EvaluationContext context) {
    final var evaluationResult = evaluateExpression(expression, context);

    assertThat(evaluationResult.isFailure())
        .describedAs(evaluationResult.getFailureMessage())
        .isFalse();

    return evaluationResult;
  }

  private EvaluationResult evaluateExpression(
      final String expression, final EvaluationContext context) {
    final var parseExpression = expressionLanguage.parseExpression("=" + expression);
    return expressionLanguage.evaluateExpression(parseExpression, context);
  }

  record FromAiExpressionTestCase<T>(
      String description,
      String expression,
      ResultType expectedResultType,
      Function<EvaluationResult, T> resultExtractor,
      T expectedResult) {

    @Override
    public String toString() {
      return "%s: %s".formatted(description, expression);
    }
  }
}
