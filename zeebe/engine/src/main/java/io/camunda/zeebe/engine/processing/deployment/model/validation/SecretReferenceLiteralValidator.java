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
import io.camunda.zeebe.el.ResultType;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import io.camunda.zeebe.util.Either;
import java.util.regex.Pattern;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

/**
 * Rejects a deployment when a {@code camunda.secrets.<name>} reference is used as a string literal
 * in an input-mapping source; only expression usage (a FEEL path) is allowed. This removes the
 * literal-vs-expression ambiguity, so a reference left in a valid input mapping is always an
 * expression.
 *
 * <p>A source is a literal when it evaluates, without any variable context, to a constant string
 * containing a reference — catching a static value, a FEEL string literal and constant-folded
 * concatenations. A runtime variable value equal to a reference cannot be checked at deploy time
 * and is an accepted, benign edge.
 */
final class SecretReferenceLiteralValidator implements ModelElementValidator<ZeebeInput> {

  private static final Pattern SECRET_REFERENCE =
      Pattern.compile("camunda\\.secrets\\.[\\p{Alnum}_]+");

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

    // a literal constant-folds to a string without a variable context; an expression path does not
    final EvaluationResult result =
        expressionLanguage.evaluateExpression(expression, variable -> Either.left(null));
    if (result.isFailure() || result.getType() != ResultType.STRING) {
      return;
    }

    final var matcher = SECRET_REFERENCE.matcher(result.getString());
    if (matcher.find()) {
      final var reference = matcher.group();
      validationResultCollector.addError(
          0,
          String.format(
              "Secret reference '%s' must be used as an expression (e.g. '=%s'), not as a string"
                  + " literal, in input mapping source '%s'.",
              reference, reference, source));
    }
  }
}
