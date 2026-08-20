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
package io.camunda.client.api.response;

import java.util.List;

/** Response for the expression evaluation command. */
public interface EvaluateExpressionResponse {

  /**
   * Returns the evaluated expression.
   *
   * @return the expression that was evaluated
   */
  String getExpression();

  /**
   * Returns the result of the expression evaluation.
   *
   * @return the result value. Type depends on the result type (String, Boolean, Number, or Object)
   */
  Object getResult();

  /**
   * Returns the list of warnings generated during expression evaluation.
   *
   * @return the list of warnings, or an empty list if none
   */
  List<EvaluationWarning> getWarnings();

  /**
   * Returns the secret references resolved from trusted sources while evaluating the expression: a
   * {@code camunda.secrets.<name>} reference used directly in the expression, or one carried by a
   * {@code SECRET_REFERENCE}-kind cluster variable the expression read. References appearing only
   * in request-body variables or plain cluster variables are excluded, so callers may safely
   * resolve exactly the references reported here.
   *
   * @return the list of referenced secrets, or an empty list if none
   */
  List<SecretReference> getReferencedSecrets();
}
