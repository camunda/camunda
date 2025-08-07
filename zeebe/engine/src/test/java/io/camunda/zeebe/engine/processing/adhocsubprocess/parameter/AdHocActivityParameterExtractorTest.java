/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.adhocsubprocess.parameter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.engine.processing.adhocsubprocess.AdHocActivityMetadata.AdHocActivityParameter;
import java.util.List;
import java.util.Map;
import org.camunda.feel.api.FeelEngineApi;
import org.camunda.feel.api.FeelEngineBuilder;
import org.camunda.feel.api.ParseResult;
import org.camunda.feel.syntaxtree.ParsedExpression;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class AdHocActivityParameterExtractorTest {

  private final FeelEngineApi feelEngine = FeelEngineBuilder.forJava().build();
  private final AdHocActivityParameterExtractor extractor = new AdHocActivityParameterExtractor();

  @ParameterizedTest
  @MethodSource("testFeelExpressionsWithExpectedParameters")
  void extractsAllParametersFromExpression(
      final AdHocActivityParameterExtractionTestCase testCase) {
    final List<AdHocActivityParameter> parameters = extractParameters(testCase.expression());

    if (testCase.expectedParameters.isEmpty()) {
      assertThat(parameters).isEmpty();
    } else {
      assertThat(parameters)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactlyElementsOf(testCase.expectedParameters());
    }
  }

  @ParameterizedTest
  @CsvSource({
    "\"toolCall.myVariable\",string 'toolCall.myVariable'",
    "10,ConstNumber(10)",
    "[],ConstList(List())"
  })
  void throwsExceptionWhenValueIsNotAReference(
      final String parameter, final String exceptionMessage) {
    assertThatThrownBy(() -> extractParameters("fromAi(%s)".formatted(parameter)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith(
            "Expected fromAi() parameter 'value' to be a reference (e.g. 'toolCall.customParameter'), but received "
                + exceptionMessage);
  }

  @Test
  void throwsExceptionWhenDescriptionValueIsNotAString() {
    assertThatThrownBy(
            () -> extractParameters("fromAi(value: toolCall.myVariable, description: 10)"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith(
            "Expected fromAi() parameter 'description' to be a string, but received '10'");
  }

  @Test
  void throwsExceptionWhenTypeValueIsNotAString() {
    assertThatThrownBy(() -> extractParameters("fromAi(value: toolCall.myVariable, type: 10)"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith(
            "Expected fromAi() parameter 'type' to be a string, but received '10'.");
  }

  @Test
  void throwsExceptionWhenSchemaValueIsNotAContext() {
    assertThatThrownBy(
            () -> extractParameters("fromAi(value: toolCall.myVariable, schema: \"dummy\")"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith(
            "Expected fromAi() parameter 'schema' to be a map, but received 'dummy'.");
  }

  @Test
  void throwsExceptionWhenOptionsValueIsNotAContext() {
    assertThatThrownBy(
            () -> extractParameters("fromAi(value: toolCall.myVariable, options: \"dummy\")"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith(
            "Expected fromAi() parameter 'options' to be a map, but received 'dummy'.");
  }

  private List<AdHocActivityParameter> extractParameters(final String expression) {
    final ParseResult parseResult = feelEngine.parseExpression(expression);
    assertThat(parseResult.isSuccess())
        .describedAs("Failed to parse expression: %s", parseResult.failure().message())
        .isTrue();

    return extractParameters(parseResult.parsedExpression());
  }

  private List<AdHocActivityParameter> extractParameters(final ParsedExpression parsedExpression) {
    return extractor.extractParameters(parsedExpression);
  }

  static List<AdHocActivityParameterExtractionTestCase>
      testFeelExpressionsWithExpectedParameters() {
    return List.of(
        new AdHocActivityParameterExtractionTestCase(
            "No parameters",
            """
            "hello"
            """),
        new AdHocActivityParameterExtractionTestCase(
            "Only expression: Name",
            """
            fromAi(toolCall.aSimpleValue)
            """,
            new AdHocActivityParameter("aSimpleValue", null, null, null, null)),
        new AdHocActivityParameterExtractionTestCase(
            "Only expression: Name + description",
            """
            fromAi(toolCall.aSimpleValue, "A simple value")
            """,
            new AdHocActivityParameter("aSimpleValue", "A simple value", null, null, null)),
        new AdHocActivityParameterExtractionTestCase(
            "Only expression: Name + description + type",
            """
            fromAi(toolCall.aSimpleValue, "A simple value", "string")
            """,
            new AdHocActivityParameter("aSimpleValue", "A simple value", "string", null, null)),
        new AdHocActivityParameterExtractionTestCase(
            "Only expression: Name + description + type + schema",
            """
            fromAi(toolCall.aSimpleValue, "A simple value", "string", { enum: ["A", "B", "C"] })
            """,
            new AdHocActivityParameter(
                "aSimpleValue",
                "A simple value",
                "string",
                Map.of("enum", List.of("A", "B", "C")),
                null)),
        new AdHocActivityParameterExtractionTestCase(
            "Only expression: Name + description + type + schema + options",
            """
            fromAi(toolCall.aSimpleValue, "A simple value", "string", { enum: ["A", "B", "C"] }, { optional: true })
            """,
            new AdHocActivityParameter(
                "aSimpleValue",
                "A simple value",
                "string",
                Map.of("enum", List.of("A", "B", "C")),
                Map.of("optional", true))),
        new AdHocActivityParameterExtractionTestCase(
            "Only expression: Name + description + type + schema + options (value not a child reference)",
            """
            fromAi(aSimpleValue, "A simple value", "string", { enum: ["A", "B", "C"] }, { optional: true })
            """,
            new AdHocActivityParameter(
                "aSimpleValue",
                "A simple value",
                "string",
                Map.of("enum", List.of("A", "B", "C")),
                Map.of("optional", true))),
        new AdHocActivityParameterExtractionTestCase(
            "Only expression: Name + description + type + schema + options (expressions to generate params)",
            """
            fromAi(toolCall.aSimpleValue, string join(["A", "simple", "value"], " "), "str" + "ing", context put({}, "enum", ["A", "B", "C"]), { optional: not(false) })
            """,
            new AdHocActivityParameter(
                "aSimpleValue",
                "A simple value",
                "string",
                Map.of("enum", List.of("A", "B", "C")),
                Map.of("optional", true))),
        new AdHocActivityParameterExtractionTestCase(
            "Only expression: Name (named params)",
            """
            fromAi(value: toolCall.aSimpleValue)
            """,
            new AdHocActivityParameter("aSimpleValue", null, null, null, null)),
        new AdHocActivityParameterExtractionTestCase(
            "Only expression: Name + description (named params)",
            """
            fromAi(value: toolCall.aSimpleValue, description: "A simple value")
            """,
            new AdHocActivityParameter("aSimpleValue", "A simple value", null, null, null)),
        new AdHocActivityParameterExtractionTestCase(
            "Only expression: Name + description + type (named params)",
            """
            fromAi(value: toolCall.aSimpleValue, description: "A simple value", type: "string")
            """,
            new AdHocActivityParameter("aSimpleValue", "A simple value", "string", null, null)),
        new AdHocActivityParameterExtractionTestCase(
            "Only expression: Name + description + type + schema (named params)",
            """
            fromAi(value: toolCall.aSimpleValue, description: "A simple value", type: "string", schema: { enum: ["A", "B", "C"] })
            """,
            new AdHocActivityParameter(
                "aSimpleValue",
                "A simple value",
                "string",
                Map.of("enum", List.of("A", "B", "C")),
                null)),
        new AdHocActivityParameterExtractionTestCase(
            "Only expression: Name + description + type + schema + options (named params)",
            """
            fromAi(
              value: toolCall.aSimpleValue,
              description: "A simple value",
              type: "string",
              schema: { enum: ["A", "B", "C"] },
              options: { optional: true }
            )
            """,
            new AdHocActivityParameter(
                "aSimpleValue",
                "A simple value",
                "string",
                Map.of("enum", List.of("A", "B", "C")),
                Map.of("optional", true))),
        new AdHocActivityParameterExtractionTestCase(
            "Only expression: Name + description + type + schema + options (named params, mixed order)",
            """
            fromAi(
              description: "A simple value",
              options: { optional: true },
              schema: { enum: ["A", "B", "C"] },
              type: "string",
              value: toolCall.aSimpleValue
            )
            """,
            new AdHocActivityParameter(
                "aSimpleValue",
                "A simple value",
                "string",
                Map.of("enum", List.of("A", "B", "C")),
                Map.of("optional", true))),
        new AdHocActivityParameterExtractionTestCase(
            "Only expression: Name + description + type + schema + options (named params, mixed order, value not a child reference)",
            """
            fromAi(
              description: "A simple value",
              options: { optional: true },
              schema: { enum: ["A", "B", "C"] },
              type: "string",
              value: aSimpleValue
            )
            """,
            new AdHocActivityParameter(
                "aSimpleValue",
                "A simple value",
                "string",
                Map.of("enum", List.of("A", "B", "C")),
                Map.of("optional", true))),
        new AdHocActivityParameterExtractionTestCase(
            "Only expression: Name + description + type + schema + options (named params, mixed order, expressions to generate params)",
            """
            fromAi(
              description: string join(["A", "simple", "value"], " "),
              options: { optional: not(false) },
              schema: context put({}, "enum", ["A", "B", "C"]),
              type: "str" + "ing",
              value: toolCall.aSimpleValue
            )
            """,
            new AdHocActivityParameter(
                "aSimpleValue",
                "A simple value",
                "string",
                Map.of("enum", List.of("A", "B", "C")),
                Map.of("optional", true))),
        new AdHocActivityParameterExtractionTestCase(
            "Array schema with sub-schema",
            """
            fromAi(toolCall.multiValue, "Select a multi value", "array", {
              "items": {
                "type": "string",
                "enum": ["foo", "bar", "baz"]
              }
            })
            """,
            new AdHocActivityParameter(
                "multiValue",
                "Select a multi value",
                "array",
                Map.of("items", Map.of("type", "string", "enum", List.of("foo", "bar", "baz"))),
                null)),
        new AdHocActivityParameterExtractionTestCase(
            "Part of operation (integer)",
            """
            1 + 2 + fromAi(toolCall.thirdValue, "The third value to add", "integer")
            """,
            new AdHocActivityParameter(
                "thirdValue", "The third value to add", "integer", null, null)),
        new AdHocActivityParameterExtractionTestCase(
            "Part of operation with conversion (integer)",
            """
            1 + 2 + number(fromAi(toolCall.thirdValue, "The third value to add", "integer"))
            """,
            new AdHocActivityParameter(
                "thirdValue", "The third value to add", "integer", null, null)),
        new AdHocActivityParameterExtractionTestCase(
            "Part of string concatenation",
            """
            "https://example.com/" + fromAi(toolCall.urlPath, "The URL path to use", "string")
            """,
            new AdHocActivityParameter("urlPath", "The URL path to use", "string", null, null)),
        new AdHocActivityParameterExtractionTestCase(
            "Part of string concatenation with conversion",
            """
            "https://example.com/" + string(fromAi(toolCall.urlPath, "The URL path to use", "string"))
            """,
            new AdHocActivityParameter("urlPath", "The URL path to use", "string", null, null)),
        new AdHocActivityParameterExtractionTestCase(
            "Multiple parameters, part of a context",
            """
            {
              foo: "bar",
              bar: fromAi(toolCall.barValue, "A good bar value", "string"),
              combined: string(fromAi(toolCall.firstOne, "The first value")) + fromAi(toolCall.secondOne, "The second value", "string")
            }
            """,
            new AdHocActivityParameter("barValue", "A good bar value", "string", null, null),
            new AdHocActivityParameter("firstOne", "The first value", null, null, null),
            new AdHocActivityParameter("secondOne", "The second value", "string", null, null)),
        new AdHocActivityParameterExtractionTestCase(
            "Multiple parameters, part of a list",
            """
            ["something", fromAi(toolCall.firstValue, "The first value", "string"), fromAi(toolCall.secondValue, "The second value", "integer")]
            """,
            new AdHocActivityParameter("firstValue", "The first value", "string", null, null),
            new AdHocActivityParameter("secondValue", "The second value", "integer", null, null)),
        new AdHocActivityParameterExtractionTestCase(
            "Multiple parameters, part of a context and list",
            """
            {
              foo: [fromAi(toolCall.firstValue, "The first value", "string"), fromAi(toolCall.secondValue, "The second value", "integer")],
              bar: {
                baz: fromAi(toolCall.thirdValue, "The third value to add")
              }
            }
            """,
            new AdHocActivityParameter("firstValue", "The first value", "string", null, null),
            new AdHocActivityParameter("secondValue", "The second value", "integer", null, null),
            new AdHocActivityParameter("thirdValue", "The third value to add", null, null, null)),
        new AdHocActivityParameterExtractionTestCase(
            "Multiple parameters, part of a context and list (named params)",
            """
            {
              foo: [
                fromAi(value: toolCall.firstValue, description: "The first value", type: "string"),
                fromAi(description: "The second value", type: "integer", value: toolCall.secondValue)
              ],
              bar: {
                baz: fromAi(value: toolCall.thirdValue, description: "The third value to add"),
                qux: fromAi(value: toolCall.fourthValue, description: "The fourth value to add", type: "array", schema: {
                  "items": {
                    "type": "string",
                    "enum": ["foo", "bar", "baz"]
                  }
                })
              }
            }
            """,
            new AdHocActivityParameter("firstValue", "The first value", "string", null, null),
            new AdHocActivityParameter("secondValue", "The second value", "integer", null, null),
            new AdHocActivityParameter("thirdValue", "The third value to add", null, null, null),
            new AdHocActivityParameter(
                "fourthValue",
                "The fourth value to add",
                "array",
                Map.of("items", Map.of("type", "string", "enum", List.of("foo", "bar", "baz"))),
                null)));
  }

  record AdHocActivityParameterExtractionTestCase(
      String description, String expression, List<AdHocActivityParameter> expectedParameters) {

    AdHocActivityParameterExtractionTestCase(
        final String description,
        final String expression,
        final AdHocActivityParameter... expectedParameters) {
      this(description, expression, List.of(expectedParameters));
    }

    @Override
    public String toString() {
      return "%s: %s".formatted(description, expression);
    }
  }
}
