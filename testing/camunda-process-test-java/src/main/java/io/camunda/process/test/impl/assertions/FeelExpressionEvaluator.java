/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.process.test.impl.assertions;

import java.util.Map;
import org.camunda.feel.api.EvaluationResult;
import org.camunda.feel.api.FeelEngineApi;
import org.camunda.feel.api.FeelEngineBuilder;
import org.camunda.feel.api.ParseResult;

/** Evaluates FEEL expressions against a map of variables. */
class FeelExpressionEvaluator {

  static final FeelExpressionEvaluator INSTANCE = new FeelExpressionEvaluator();

  private final FeelEngineApi feelEngine;

  FeelExpressionEvaluator() {
    feelEngine = FeelEngineBuilder.forJava().build();
  }

  /**
   * Validates a FEEL expression by attempting to parse it. Call this before entering a retry loop
   * to fail fast on non-recoverable syntax errors.
   *
   * @param expression the FEEL expression (without the leading {@code =} prefix)
   * @throws IllegalArgumentException if the expression is null, blank, starts with {@code =}, or
   *     contains a syntax error
   */
  void validate(final String expression) {
    final String normalized = normalizeExpression(expression);
    final ParseResult parseResult = feelEngine.parseExpression(normalized);
    if (!parseResult.isSuccess()) {
      throw new IllegalArgumentException(
          "FEEL expression '"
              + expression
              + "' is not a valid FEEL expression: "
              + parseResult.failure().message());
    }
  }

  /**
   * Evaluates a FEEL expression with the given variables as context.
   *
   * @param expression the FEEL expression (without the leading {@code =} prefix)
   * @param variables the context variables available to the expression
   * @return the evaluation result
   * @throws IllegalArgumentException if the expression is null, blank, or starts with {@code =}
   */
  EvaluationResult evaluate(final String expression, final Map<String, Object> variables) {
    final String normalized = normalizeExpression(expression);
    return feelEngine.evaluateExpression(normalized, variables);
  }

  private String normalizeExpression(final String expression) {
    final String normalized = expression != null ? expression.trim() : "";
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("FEEL expression must not be null or blank.");
    }
    if (normalized.startsWith("=")) {
      throw new IllegalArgumentException(
          "FEEL expression must not start with '='. "
              + "The '=' prefix is a Camunda expression language marker and is not part of the FEEL expression itself. "
              + "Please provide the expression without the leading '=', e.g., \""
              + normalized.substring(1).trim()
              + "\" instead of \""
              + normalized
              + "\".");
    }
    return normalized;
  }
}
