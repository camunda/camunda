/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.processor.workflow.deployment.model.validation;

import io.zeebe.msgpack.el.CompiledJsonCondition;
import io.zeebe.msgpack.el.JsonConditionFactory;
import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import io.zeebe.msgpack.jsonpath.JsonPathQueryCompiler;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ZeebeExpressionValidator {

  public void validateExpression(String expression, ValidationResultCollector resultCollector) {
    final CompiledJsonCondition condition = JsonConditionFactory.createCondition(expression);

    if (!condition.isValid()) {
      resultCollector.addError(
          0, String.format("Condition expression is invalid: %s", condition.getErrorMessage()));
    }
  }

  public void validateJsonPath(String jsonPath, ValidationResultCollector resultCollector) {
    if (jsonPath == null) {
      resultCollector.addError(0, String.format("JSON path query is empty"));
      return;
    }

    final JsonPathQueryCompiler queryCompiler = new JsonPathQueryCompiler();
    final JsonPathQuery compiledQuery = queryCompiler.compile(jsonPath);

    if (!compiledQuery.isValid()) {
      resultCollector.addError(
          0, String.format("JSON path query is invalid: %s", compiledQuery.getErrorReason()));
    }
  }
}
