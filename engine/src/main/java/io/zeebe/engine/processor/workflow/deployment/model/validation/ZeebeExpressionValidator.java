/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.validation;

import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import io.zeebe.msgpack.jsonpath.JsonPathQueryCompiler;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public final class ZeebeExpressionValidator<T extends ModelElementInstance>
    implements ModelElementValidator<T> {

  private final Class<T> elementType;
  private final List<Function<T, String>> expressionSuppliers;

  private ZeebeExpressionValidator(
      Class<T> elementType, List<Function<T, String>> expressionSuppliers) {
    this.elementType = elementType;
    this.expressionSuppliers = expressionSuppliers;
  }

  private void validatePathQuery(String jsonPath, ValidationResultCollector resultCollector) {
    if (jsonPath == null || jsonPath.isEmpty()) {
      return;
    }

    final JsonPathQueryCompiler queryCompiler = new JsonPathQueryCompiler();
    final JsonPathQuery compiledQuery = queryCompiler.compile(jsonPath);

    if (!compiledQuery.isValid()) {
      resultCollector.addError(
          0, String.format("JSON path query is invalid: %s", compiledQuery.getErrorReason()));
    }
  }

  @Override
  public Class<T> getElementType() {
    return elementType;
  }

  @Override
  public void validate(T element, ValidationResultCollector validationResultCollector) {

    expressionSuppliers.forEach(
        supplier -> {
          final var expression = supplier.apply(element);
          validatePathQuery(expression, validationResultCollector);
        });
  }

  public static <T extends ModelElementInstance> ZeebeExpressionValidator.Builder<T> verifyThat(
      Class<T> elementType) {
    return new ZeebeExpressionValidator.Builder<>(elementType);
  }

  public static class Builder<T extends ModelElementInstance> {

    private final Class<T> elementType;
    private final List<Function<T, String>> expressionSuppliers = new ArrayList<>();

    public Builder(Class<T> elementType) {
      this.elementType = elementType;
    }

    public Builder<T> hasValidPathExpression(Function<T, String> expressionSupplier) {
      expressionSuppliers.add(expressionSupplier);
      return this;
    }

    public ZeebeExpressionValidator<T> build() {

      return new ZeebeExpressionValidator<>(elementType, expressionSuppliers);
    }
  }
}
