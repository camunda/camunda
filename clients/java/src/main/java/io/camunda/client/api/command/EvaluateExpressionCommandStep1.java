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
package io.camunda.client.api.command;

import io.camunda.client.api.response.EvaluateExpressionResponse;
import java.io.InputStream;
import java.util.Map;

/**
 * Command to evaluate a FEEL expression.
 *
 * <p>The expression can optionally be evaluated in the variable scope of a process instance (via
 * {@link EvaluateExpressionCommandStep2#processInstanceKey(long)}) or an element instance (via
 * {@link EvaluateExpressionCommandStep2#elementInstanceKey(long)}). These two options are mutually
 * exclusive: setting both on the same builder causes {@link EvaluateExpressionCommandStep2#send()}
 * to fail with an {@link IllegalStateException}. If neither is set, the expression is evaluated
 * against tenant-scoped cluster variables and the request-body variables only.
 *
 * <p>When variables passed via {@link EvaluateExpressionCommandStep2#variables(Map)} share a key
 * with engine variables resolved from the process or element context, the value from {@code
 * variables} takes precedence for the current evaluation.
 */
public interface EvaluateExpressionCommandStep1 {

  /**
   * Sets the expression to evaluate.
   *
   * @param expression the expression to evaluate (e.g., "=x + y")
   * @return the builder for this command
   */
  EvaluateExpressionCommandStep2 expression(String expression);

  interface EvaluateExpressionCommandStep2
      extends CommandWithTenantStep<EvaluateExpressionCommandStep2>,
          FinalCommandStep<EvaluateExpressionResponse>,
          CommandWithVariables<EvaluateExpressionCommandStep2> {

    /**
     * Set the variables for the expression evaluation.
     *
     * @param variables the variables JSON document as String
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    @Override
    EvaluateExpressionCommandStep2 variables(String variables);

    /**
     * Set the variables for the expression evaluation.
     *
     * @param variables the variables document as object to be serialized to JSON
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    @Override
    EvaluateExpressionCommandStep2 variables(Object variables);

    /**
     * Set the variables for the expression evaluation.
     *
     * @param variables the variables JSON document as stream
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    @Override
    EvaluateExpressionCommandStep2 variables(InputStream variables);

    /**
     * Set the variables for the expression evaluation.
     *
     * @param variables the variables document as map
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    @Override
    EvaluateExpressionCommandStep2 variables(Map<String, Object> variables);

    /**
     * Set a single variable for the expression evaluation.
     *
     * @param key the key of the variable as string
     * @param value the value of the variable as object
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    @Override
    EvaluateExpressionCommandStep2 variable(String key, Object value);

    /**
     * Evaluate the expression in the variable scope of the given process instance. Engine variables
     * visible at the process-instance scope are exposed to the expression in addition to the
     * request-body variables and tenant-scoped cluster variables.
     *
     * <p>When a key is present both in the request-body {@code variables} and in the engine
     * variable scope, the value from {@code variables} takes precedence for this evaluation only.
     *
     * <p>Mutually exclusive with {@link #elementInstanceKey(long)}: setting both on the same
     * builder causes {@link #send()} to fail with an {@link IllegalStateException}.
     *
     * @param processInstanceKey the key of the process instance whose variable scope is exposed to
     *     the expression
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    EvaluateExpressionCommandStep2 processInstanceKey(long processInstanceKey);

    /**
     * Evaluate the expression in the variable scope of the given element instance. Engine variables
     * visible at the element-instance scope (including parent scopes up to the process instance)
     * are exposed to the expression in addition to the request-body variables and tenant-scoped
     * cluster variables.
     *
     * <p>When a key is present both in the request-body {@code variables} and in the engine
     * variable scope, the value from {@code variables} takes precedence for this evaluation only.
     *
     * <p>Mutually exclusive with {@link #processInstanceKey(long)}: setting both on the same
     * builder causes {@link #send()} to fail with an {@link IllegalStateException}.
     *
     * @param elementInstanceKey the key of the element instance whose variable scope is exposed to
     *     the expression
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    EvaluateExpressionCommandStep2 elementInstanceKey(long elementInstanceKey);
  }
}
