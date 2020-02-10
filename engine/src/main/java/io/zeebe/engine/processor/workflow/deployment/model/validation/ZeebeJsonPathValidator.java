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

public final class ZeebeJsonPathValidator<T extends ModelElementInstance>
    implements ModelElementValidator<T> {

  private final Class<T> elementType;
  private final List<Function<T, String>> expressionSuppliers;

  private ZeebeJsonPathValidator(
      final Class<T> elementType, final List<Function<T, String>> expressionSuppliers) {
    this.elementType = elementType;
    this.expressionSuppliers = expressionSuppliers;
  }

  private void validatePathQuery(
      final String jsonPath, final ValidationResultCollector resultCollector) {
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
  public void validate(final T element, final ValidationResultCollector validationResultCollector) {

    expressionSuppliers.forEach(
        supplier -> {
          final var expression = supplier.apply(element);
          validatePathQuery(expression, validationResultCollector);
        });
  }

  public static <T extends ModelElementInstance> ZeebeJsonPathValidator.Builder<T> verifyThat(
      final Class<T> elementType) {
    return new ZeebeJsonPathValidator.Builder<>(elementType);
  }

  public static class Builder<T extends ModelElementInstance> {

    private final Class<T> elementType;
    private final List<Function<T, String>> expressionSuppliers = new ArrayList<>();

    public Builder(final Class<T> elementType) {
      this.elementType = elementType;
    }

    public Builder<T> hasValidPathExpression(final Function<T, String> expressionSupplier) {
      expressionSuppliers.add(expressionSupplier);
      return this;
    }

    public ZeebeJsonPathValidator<T> build() {

      return new ZeebeJsonPathValidator<>(elementType, expressionSuppliers);
    }
  }
}
