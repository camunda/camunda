/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.ExpressionTransformer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public final class ZeebeExpressionValidator<T extends ModelElementInstance>
    implements ModelElementValidator<T> {

  private static final long NO_VARIABLE_SCOPE = -1L;

  private static final Pattern PATH_PATTERN =
      Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*");

  private static final List<String> PATH_RESERVED_WORDS =
      List.of(
          "null",
          "true",
          "false",
          "function",
          "if",
          "then",
          "else",
          "for",
          "between",
          "instance",
          "of");

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

    final var isReservedWord = PATH_RESERVED_WORDS.contains(expression);
    if (isReservedWord) {
      resultCollector.addError(
          0,
          String.format(
              "Expected path expression '%s' but is one of the reserved words (%s).",
              expression, String.join(", ", PATH_RESERVED_WORDS)));
    }
  }

  public static boolean isListOfCsv(final Expression staticExp) {
    return ExpressionTransformer.parseListOfCsv(staticExp.getExpression()).isRight();
  }

  public static boolean isValidDateTime(
      final Expression staticExp, final ExpressionProcessor expressionProcessor) {
    return expressionProcessor.evaluateDateTimeExpression(staticExp, NO_VARIABLE_SCOPE).isRight();
  }

  public static class Builder<T extends ModelElementInstance> {

    private final Class<T> elementType;
    private final List<Verification<T>> verifications = new ArrayList<>();

    public Builder(final Class<T> elementType) {
      this.elementType = elementType;
    }

    public Builder<T> hasValidExpression(
        final Function<T, String> expressionSupplier,
        final Consumer<ExpressionVerification> expressionVerification) {

      final var expressionV = new ExpressionVerification();
      expressionVerification.accept(expressionV);

      verifications.add(new Verification<>(expressionSupplier, expressionV.build()));
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

  public static final class ExpressionVerification {

    private final List<ExpressionRequirement<Expression>> staticRequirements = new ArrayList<>();
    private boolean isNonStatic = false;
    private boolean isMandatory = false;

    public ExpressionVerification isNonStatic() {
      isNonStatic = true;
      return this;
    }

    public ExpressionVerification isMandatory() {
      isMandatory = true;
      return this;
    }

    public ExpressionVerification isOptional() {
      isMandatory = false;
      return this;
    }

    public ExpressionVerification satisfiesIfStatic(
        final Predicate<Expression> verification, final String description) {
      staticRequirements.add(new ExpressionRequirement<>(verification, description));
      return this;
    }

    private Assertion build() {
      return ((expression, expressionLanguage, resultCollector) -> {
        if (expression == null) {
          if (isMandatory) {
            resultCollector.addError(0, "Expected expression but not found.");
          }
          return;
        }

        final var parseResult = expressionLanguage.parseExpression(expression);

        if (!parseResult.isValid()) {
          resultCollector.addError(0, parseResult.getFailureMessage());
          return;
        }

        assertStaticExpression(expression, resultCollector, parseResult);
      });
    }

    private void assertStaticExpression(
        final String expression,
        final ValidationResultCollector resultCollector,
        final Expression parseResult) {
      if (!parseResult.isStatic()) {
        return;
      }

      if (isNonStatic) {
        resultCollector.addError(
            0,
            String.format(
                "Expected expression but found static value '%s'. An expression must start with '=' (e.g. '=%s').",
                expression, expression));
        return;
      }

      staticRequirements.stream()
          .filter(req -> req.negate().test(parseResult))
          .map(
              req ->
                  String.format(
                      "Expected static value to %s, but found '%s'.",
                      req.getDescription(), expression))
          .forEach(failure -> resultCollector.addError(0, failure));
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

  /**
   * Combines an expression predicate with a description.
   *
   * @param <E> The type of expression
   */
  private static final class ExpressionRequirement<E extends Expression> implements Predicate<E> {

    private final Predicate<E> predicate;
    private final String description;

    public ExpressionRequirement(final Predicate<E> predicate, final String description) {
      this.predicate = predicate;
      this.description = description;
    }

    @Override
    public boolean test(final E e) {
      return predicate.test(e);
    }

    public String getDescription() {
      return description;
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
