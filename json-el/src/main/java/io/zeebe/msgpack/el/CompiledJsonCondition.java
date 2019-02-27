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
package io.zeebe.msgpack.el;

import java.util.Set;
import org.agrona.DirectBuffer;
import scala.collection.JavaConverters;

public final class CompiledJsonCondition {
  private final String expression;
  private final JsonCondition condition;
  private final boolean isValid;
  private final String errorMessage;

  private CompiledJsonCondition(
      String expression, JsonCondition condition, boolean isValid, String errorMessage) {
    this.expression = expression;
    this.condition = condition;
    this.isValid = isValid;
    this.errorMessage = errorMessage;
  }

  public static CompiledJsonCondition success(String expression, JsonCondition condition) {
    return new CompiledJsonCondition(expression, condition, true, null);
  }

  public static CompiledJsonCondition fail(String expression, String errorMessage) {
    return new CompiledJsonCondition(expression, null, false, errorMessage);
  }

  public String getExpression() {
    return expression;
  }

  public JsonCondition getCondition() {
    return condition;
  }

  public boolean isValid() {
    return isValid;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public Set<DirectBuffer> getVariableNames() {
    return JavaConverters.setAsJavaSet(condition.variableNames());
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("CompiledJsonCondition [expression=");
    builder.append(expression);
    builder.append(", errorMessage=");
    builder.append(errorMessage);
    builder.append("]");
    return builder.toString();
  }
}
