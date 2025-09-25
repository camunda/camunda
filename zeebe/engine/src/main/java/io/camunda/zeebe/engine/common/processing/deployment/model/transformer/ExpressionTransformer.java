/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.deployment.model.transformer;

import io.camunda.zeebe.engine.common.processing.common.Failure;
import io.camunda.zeebe.util.Either;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

/**
 * Utility class to help with transforming expressions from strings to objects (i.e. parsing) and
 * from objects to strings (i.e. serializing).
 */
public final class ExpressionTransformer {

  private ExpressionTransformer() {
    throw new IllegalStateException(
        "Expected to instantiate this class, but it is a Utility class");
  }

  /**
   * Parses a static value as a list of CSV, trimming any whitespace.
   *
   * @param value the static value to parse
   * @return either a failure or a list of values (trimmed)
   */
  public static Either<Failure, List<String>> parseListOfCsv(final String value) {
    if (value.isEmpty()) {
      return Either.right(List.of());
    }

    final List<String> values =
        Arrays.stream(value.split(","))
            .filter(Predicate.not(String::isBlank))
            .map(String::trim)
            .collect(Collectors.toList());
    if (values.size() < StringUtils.countMatches(value, ",") + 1) {
      // one of the values was a blank string, e.g. 'a, ,c'
      return Either.left(
          new Failure("Expected to parse list of CSV, but " + value + " is not CSV"));
    }

    return Either.right(values);
  }

  /**
   * Serializes a list of strings to a list-literal, e.g. {@code List.of("a","b") =>
   * "[\"a\",\"b\"]"}.
   *
   * @param values the list of string values to transform
   * @return a string representation of the list literal
   */
  public static String asListLiteral(final List<String> values) {
    return values.stream()
        .map(ExpressionTransformer::asStringLiteral)
        .collect(Collectors.joining(",", "[", "]"));
  }

  /**
   * Transforms a string value to a string literal, e.g. {@code "a" => "\"a\""}.
   *
   * @param value the string value to transform
   * @return a string representation of the string literal
   */
  public static String asStringLiteral(final String value) {
    return String.format("\"%s\"", value);
  }

  /**
   * Transforms an expression string into a FEEL expression string.
   *
   * <p>Zeebe considers strings starting with `=` as FEEL expressions, and other strings as static
   * values. For example, `author` is considered a static value `author`, while `= author` is
   * considered a FEEL expression that evaluates to the value of the variable `author`.
   *
   * @param expression The actual expression string to convert into a FEEL expression
   * @return The provided expression string prefixed by the `=` character
   */
  public static String asFeelExpressionString(final String expression) {
    return String.format("=%s", expression);
  }
}
