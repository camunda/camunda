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
import java.util.regex.Pattern;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public final class ZeebeExpressionValidator<T extends ModelElementInstance>
    implements ModelElementValidator<T> {

  private static final Pattern PATH_PATTERN =
      Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*");

  private final ExpressionLanguage expressionLanguage;
  private final Class<T> elementType;
  private final List<Verification<T>> verifications;

  private ZeebeExpressionValidator(
      final ExpressionLanguage expressionLanguage,
      final Class<T> elementType,
      final List<Verification<T>> verifications) {
    this.expressionLanguage = expressionLanguage;
    this.elementType = elementType;
    this.verifications = verifications;
  }

  @Override
  public Class<T> getElementType() {
    return elementType;
  }

  @Override
  public void validate(final T element, final ValidationResultCollector validationResultCollector) {

    verifications.forEach(
        verification -> {
          final String expression = verification.expressionSupplier.apply(element);
          verification.assertion.verify(expression, expressionLanguage, validationResultCollector);
        });
  }

  public static <T extends ModelElementInstance> ZeebeExpressionValidator.Builder<T> verifyThat(
      final Class<T> elementType) {
    return new ZeebeExpressionValidator.Builder<>(elementType);
  }

  private static void verifyExpression(
      final String expression,
      final ExpressionLanguage expressionLanguage,
      final ValidationResultCollector resultCollector) {

    if (expression == null) {
      resultCollector.addError(0, "Expected expression but not found.");
      return;
    }

    final var parseResult = expressionLanguage.parseExpression(expression);

    if (!parseResult.isValid()) {
      resultCollector.addError(0, parseResult.getFailureMessage());
    }
  }

  private static void verifyNonStaticExpression(
      final String expression,
      final ExpressionLanguage expressionLanguage,
      final ValidationResultCollector resultCollector) {

    if (expression == null) {
      resultCollector.addError(0, "Expected expression but not found.");
      return;
    }

    final var parseResult = expressionLanguage.parseExpression(expression);

    if (!parseResult.isValid()) {
      resultCollector.addError(0, parseResult.getFailureMessage());
    }

    if (parseResult.isStatic()) {
      resultCollector.addError(
          0,
          String.format(
              "Expected expression but found static value '%s'. An expression must start with '=' (e.g. '=%s').",
              expression, expression));
    }
  }

  private static void verifyPath(
      final String expression,
      final ExpressionLanguage expressionLanguage,
      final ValidationResultCollector resultCollector) {

    if (expression == null || expression.isEmpty()) {
      resultCollector.addError(0, "Expected path expression but not found.");
      return;
    }

    final var matcher = PATH_PATTERN.matcher(expression);

    if (!matcher.matches()) {
      resultCollector.addError(
          0,
          String.format(
              "Expected path expression '%s' but doesn't match the pattern '%s'.",
              expression, PATH_PATTERN));
    }
  }

  public static class Builder<T extends ModelElementInstance> {

    private final Class<T> elementType;
    private final List<Verification<T>> verifications = new ArrayList<>();

    public Builder(final Class<T> elementType) {
      this.elementType = elementType;
    }

    public Builder<T> hasValidExpression(final Function<T, String> expressionSupplier) {
      verifications.add(
          new Verification<>(expressionSupplier, ZeebeExpressionValidator::verifyExpression));
      return this;
    }

    public Builder<T> hasValidNonStaticExpression(final Function<T, String> expressionSupplier) {
      verifications.add(
          new Verification<>(
              expressionSupplier, ZeebeExpressionValidator::verifyNonStaticExpression));
      return this;
    }

    public Builder<T> hasValidPath(final Function<T, String> expressionSupplier) {
      verifications.add(
          new Verification<>(expressionSupplier, ZeebeExpressionValidator::verifyPath));
      return this;
    }

    public ZeebeExpressionValidator<T> build(final ExpressionLanguage expressionLanguage) {

      return new ZeebeExpressionValidator<>(expressionLanguage, elementType, verifications);
    }
  }

  private static final class Verification<T> {
    private final Function<T, String> expressionSupplier;
    private final Assertion assertion;

    private Verification(final Function<T, String> expressionSupplier, final Assertion assertion) {
      this.expressionSupplier = expressionSupplier;
      this.assertion = assertion;
    }
  }

  @FunctionalInterface
  private interface Assertion {
    void verify(
        String expression,
        ExpressionLanguage expressionLanguage,
        ValidationResultCollector resultCollector);
  }
}
