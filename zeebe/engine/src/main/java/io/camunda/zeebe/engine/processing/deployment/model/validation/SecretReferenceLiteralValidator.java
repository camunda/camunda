/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.el.EvaluationResult;
import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.util.Either;
import java.util.LinkedHashSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Rejects a deployment when a {@code camunda.secrets.<name>} reference is used as a string literal
 * in an input-mapping source; only expression usage (a FEEL path) is allowed. This removes the
 * literal-vs-expression ambiguity, so a reference left in a valid input mapping is always an
 * expression.
 *
 * <p>A source is a literal when it evaluates, without any variable context, to a constant that
 * contains a reference — catching a static value, a FEEL string literal, constant-folded
 * concatenations, and references embedded in object or list literals (e.g. {@code ={"auth":
 * "camunda.secrets.token"}}). A runtime variable value equal to a reference cannot be checked at
 * deploy time and is an accepted, benign edge.
 */
@NullMarked
final class SecretReferenceLiteralValidator implements ModelElementValidator<ZeebeInput> {

  // unicode-aware so names with non-ASCII letters/digits are matched (and reported) in full
  private static final Pattern SECRET_REFERENCE =
      Pattern.compile("camunda\\.secrets\\.[\\p{L}\\p{N}_]+");

  private final ExpressionLanguage expressionLanguage;

  SecretReferenceLiteralValidator(final ExpressionLanguage expressionLanguage) {
    this.expressionLanguage = expressionLanguage;
  }

  @Override
  public Class<ZeebeInput> getElementType() {
    return ZeebeInput.class;
  }

  @Override
  public void validate(
      final ZeebeInput element, final ValidationResultCollector validationResultCollector) {
    final String source = element.getSource();
    if (source == null) {
      return;
    }

    final Expression expression = expressionLanguage.parseExpression(source);
    if (!expression.isValid()) {
      // invalid expressions are reported separately by the expression validator
      return;
    }

    // a literal constant-folds without a variable context; an expression path resolves to null
    final EvaluationResult result =
        expressionLanguage.evaluateExpression(expression, variable -> Either.left(null));
    final String constant = constantText(result);
    if (constant == null) {
      return;
    }

    final var matcher = SECRET_REFERENCE.matcher(constant);
    final var references = new LinkedHashSet<String>();
    while (matcher.find()) {
      references.add(matcher.group());
    }
    if (!references.isEmpty()) {
      final var formatted =
          references.stream()
              .map(reference -> "'" + reference + "'")
              .collect(Collectors.joining(", "));
      validationResultCollector.addError(
          0,
          String.format(
              "Secret reference(s) %s must be used as an expression (e.g. '=camunda.secrets.<name>'),"
                  + " not as a string literal, in input mapping source '%s'.",
              formatted, source));
    }
  }

  /**
   * The constant text of a context-free evaluation to scan for a reference, or {@code null} when
   * the result is not a constant that can embed a reference (a failure, or a non-textual type such
   * as a number). Object and list literals are inspected via their JSON form so a reference nested
   * inside them is caught too.
   */
  private @Nullable String constantText(final EvaluationResult result) {
    if (result.isFailure()) {
      return null;
    }
    return switch (result.getType()) {
      case STRING -> result.getString();
      case OBJECT, ARRAY -> MsgPackConverter.convertToJson(result.toBuffer());
      default -> null;
    };
  }
}
