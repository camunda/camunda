/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.validation;

import io.zeebe.el.ExpressionLanguage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public final class ZeebeExpressionValidator<T extends ModelElementInstance>
    implements ModelElementValidator<T> {

  private final ExpressionLanguage expressionLanguage;
  private final Class<T> elementType;
  private final List<Function<T, String>> expressionSuppliers;

  private ZeebeExpressionValidator(
      final ExpressionLanguage expressionLanguage,
      final Class<T> elementType,
      final List<Function<T, String>> expressionSuppliers) {
    this.expressionLanguage = expressionLanguage;
    this.elementType = elementType;
    this.expressionSuppliers = expressionSuppliers;
  }

  private void validateExpression(
      final String expression, final ValidationResultCollector resultCollector) {

    if (expression == null || expression.isEmpty()) {
      return;
    }

    final var parseResult = expressionLanguage.parseExpression(expression);

    if (!parseResult.isValid()) {
      resultCollector.addError(0, parseResult.getFailureMessage());
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
          validateExpression(expression, validationResultCollector);
        });
  }

  public static <T extends ModelElementInstance> ZeebeExpressionValidator.Builder<T> verifyThat(
      final Class<T> elementType) {
    return new ZeebeExpressionValidator.Builder<>(elementType);
  }

  public static class Builder<T extends ModelElementInstance> {

    private final Class<T> elementType;
    private final List<Function<T, String>> expressionSuppliers = new ArrayList<>();

    public Builder(final Class<T> elementType) {
      this.elementType = elementType;
    }

    public Builder<T> hasValidExpression(final Function<T, String> expressionSupplier) {
      expressionSuppliers.add(expressionSupplier);
      return this;
    }

    public ZeebeExpressionValidator<T> build(final ExpressionLanguage expressionLanguage) {

      return new ZeebeExpressionValidator<>(expressionLanguage, elementType, expressionSuppliers);
    }
  }
}
