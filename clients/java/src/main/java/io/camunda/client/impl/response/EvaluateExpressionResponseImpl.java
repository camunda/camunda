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
package io.camunda.client.impl.response;

import io.camunda.client.api.response.EvaluateExpressionResponse;
import io.camunda.client.protocol.rest.ExpressionEvaluationResult;
import java.util.Collections;
import java.util.List;

public class EvaluateExpressionResponseImpl implements EvaluateExpressionResponse {

  private final String expression;
  private final Object result;
  private final List<String> warnings;

  public EvaluateExpressionResponseImpl(final ExpressionEvaluationResult response) {
    expression = response.getExpression();
    result = response.getResult();
    warnings = response.getWarnings() != null ? response.getWarnings() : Collections.emptyList();
  }

  @Override
  public String getExpression() {
    return expression;
  }

  @Override
  public Object getResult() {
    return result;
  }

  @Override
  public List<String> getWarnings() {
    return warnings;
  }
}
