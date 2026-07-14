/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import java.util.LinkedHashSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;
import org.jspecify.annotations.NullMarked;

/**
 * Rejects a deployment when a {@code camunda.secrets.<name>} reference is used as a string literal
 * in an input-mapping source; only expression usage (a FEEL path) is allowed. This removes the
 * literal-vs-expression ambiguity, so a reference left in a valid input mapping is always an
 * expression.
 *
 * <p>Detection is purely static: a static value (no leading {@code =}) is scanned as a whole, and a
 * FEEL expression is scanned only inside its double-quoted string literals. A bare path such as
 * {@code =camunda.secrets.token} is never quoted and is allowed; a quoted occurrence, including one
 * nested in an object or list literal (e.g. {@code ={"auth": "camunda.secrets.token"}}), is
 * rejected. The source is never evaluated, so a pathological expression cannot break the
 * deployment.
 */
@NullMarked
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

    // a static value is a literal in full; a FEEL expression is a literal only where it is quoted
    final String literalText = expression.isStatic() ? source : stringLiterals(source.substring(1));

    final var matcher = SECRET_REFERENCE.matcher(literalText);
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
   * The concatenated contents of every double-quoted string literal in a FEEL expression body,
   * separated so adjacent literals cannot form a spurious match. Text outside string literals (such
   * as a {@code camunda.secrets.token} path expression) is skipped.
   */
  private static String stringLiterals(final String feelBody) {
    final StringBuilder literals = new StringBuilder();
    boolean insideString = false;
    for (int i = 0; i < feelBody.length(); i++) {
      final char c = feelBody.charAt(i);
      if (insideString) {
        if (c == '\\' && i + 1 < feelBody.length()) {
          // keep the escaped character as-is; enough to scan for a reference
          literals.append(feelBody.charAt(++i));
        } else if (c == '"') {
          insideString = false;
          literals.append('\n');
        } else {
          literals.append(c);
        }
      } else if (c == '"') {
        insideString = true;
      }
    }
    return literals.toString();
  }
}
