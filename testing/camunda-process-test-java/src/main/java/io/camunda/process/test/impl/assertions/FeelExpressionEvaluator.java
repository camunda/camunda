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

/** Evaluates FEEL expressions against a map of variables. */
class FeelExpressionEvaluator {

  static final FeelExpressionEvaluator INSTANCE = new FeelExpressionEvaluator();

  private final FeelEngineApi feelEngine;

  FeelExpressionEvaluator() {
    feelEngine = FeelEngineBuilder.forJava().build();
  }

  /**
   * Evaluates a FEEL expression with the given variables as context.
   *
   * @param expression the FEEL expression (without the leading {@code =} prefix)
   * @param variables the context variables available to the expression
   * @return the evaluation result
   * @throws IllegalArgumentException if the expression starts with {@code =}
   */
  EvaluationResult evaluate(final String expression, final Map<String, Object> variables) {
    if (expression != null && expression.startsWith("=")) {
      throw new IllegalArgumentException(
          "FEEL expression must not start with '='. "
              + "The '=' prefix is a Camunda expression language marker and is not part of the FEEL expression itself. "
              + "Please provide the expression without the leading '=', e.g., \""
              + expression.substring(1).trim()
              + "\" instead of \""
              + expression
              + "\".");
    }
    return feelEngine.evaluateExpression(expression, variables);
  }
}
