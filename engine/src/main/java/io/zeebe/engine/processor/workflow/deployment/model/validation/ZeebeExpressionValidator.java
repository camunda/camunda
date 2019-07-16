/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
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
