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

/** Command to evaluate an expression. */
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
  }
}
